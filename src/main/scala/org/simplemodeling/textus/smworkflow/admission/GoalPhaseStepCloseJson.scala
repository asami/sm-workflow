package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import org.simplemodeling.textus.smworkflow.value.{StepCloseRequest, StepReviewEvidence}

/**
 * Strict fixture transport for the CML StepCloseRequest payload.
 * @since   Sep. 26, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseStepCloseJson {
  val schemaVersion = "sm-workflow.step-close.v2"
  private val legacySchemaVersion = "sm-workflow.step-close.v1"

  val payloadCodec: WorkflowResultJsonV1.PayloadCodec[StepCloseRequest] =
    new WorkflowResultJsonV1.PayloadCodec[StepCloseRequest] {
      val typeIdentity = "StepCloseRequest"

      def encode(value: StepCloseRequest): Json = Json.obj(
        "submissionIdentity" -> Json.fromString(value.submissionIdentity),
        "stepIdentity" -> Json.fromString(value.stepIdentity),
        "programDigest" -> Json.fromString(value.programDigest),
        "planningDigest" -> Json.fromString(value.planningDigest),
        "reviewEvidence" -> Json.arr(value.reviewEvidence.map(encodeEvidence)*)
      )

      def decode(value: Json): Either[String, StepCloseRequest] = for {
        fields <- _fields(value, Set("submissionIdentity", "stepIdentity", "programDigest",
          "planningDigest", "reviewEvidence"))
        submission <- _string(fields, "submissionIdentity")
        step <- _string(fields, "stepIdentity")
        program <- _string(fields, "programDigest")
        planning <- _string(fields, "planningDigest")
        items <- fields("reviewEvidence").flatMap(_.asArray)
          .toRight("reviewEvidence must be an array")
        evidence <- items.foldLeft[Either[String, Vector[StepReviewEvidence]]](Right(Vector.empty)) {
          (prior, item) => for {
            accumulated <- prior
            decoded <- decodeEvidence(item)
          } yield accumulated :+ decoded
        }
      } yield StepCloseRequest(submission, step, program, planning, evidence)
    }

  def encode(request: StepCloseRequest): String = Json.obj(
    "schemaVersion" -> Json.fromString(schemaVersion),
    "request" -> payloadCodec.encode(request)
  ).noSpaces

  def decodeC(wire: String): Consequence[StepCloseRequest] = {
    if (wire == null)
      return Consequence.stateConflict("Step-close JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid Step-close JSON")
      root <- _fields(json, Set("schemaVersion", "request"))
      version <- _string(root, "schemaVersion")
      _ <- Either.cond(version == schemaVersion || version == legacySchemaVersion, (), "unsupported Step-close JSON schema")
      requestJson <- root("request").toRight("Step-close JSON has no request")
      request <- payloadCodec.decode(requestJson)
    } yield request
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private[admission] def encodeEvidence(value: StepReviewEvidence): Json = Json.obj(
    "evidenceIdentity" -> Json.fromString(value.evidenceIdentity),
    "reviewedRevision" -> Json.fromString(value.reviewedRevision),
    "reviewedAt" -> Json.fromString(value.reviewedAt),
    "scope" -> Json.fromString(value.scope),
    "summary" -> Json.fromString(value.summary),
    "provenance" -> Json.fromString(value.provenance)
  )

  private[admission] def decodeEvidence(value: Json): Either[String, StepReviewEvidence] = for {
    fields <- value.asObject.filter { fields =>
      val required = Set("evidenceIdentity", "reviewedRevision", "reviewedAt", "scope", "summary", "provenance")
      fields.keys.toSet == required || fields.keys.toSet == required + "validUntil"
    }.toRight("Step review has an incompatible shape")
    identity <- _string(fields, "evidenceIdentity")
    revision <- _string(fields, "reviewedRevision")
    reviewedAt <- _string(fields, "reviewedAt")
    scope <- _string(fields, "scope")
    summary <- _string(fields, "summary")
    provenance <- _string(fields, "provenance")
  } yield StepReviewEvidence(identity, revision, reviewedAt, scope, summary, provenance)

  private def _fields(value: Json, required: Set[String]): Either[String, JsonObject] =
    value.asObject match {
      case Some(fields) if fields.keys.toSet == required => Right(fields)
      case _ => Left("Step-close JSON has an incompatible shape")
    }

  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"Step-close JSON is missing $name")
}
