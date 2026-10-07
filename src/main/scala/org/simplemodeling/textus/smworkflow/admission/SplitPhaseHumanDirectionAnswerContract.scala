package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionSemantic as S

/** Closed passive answers; resolution references are not planning authority.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
private[smworkflow] object SplitPhaseHumanDirectionAnswerContract {
  enum Answer {
    case ProvideResolution(reference: S.ContextReference)
    case Defer
    case Cancel
  }
  val choices = Vector("ProvideResolution", "Defer", "Cancel")
  val schema = "sm-workflow.split-human-direction-answer.v1"
  val resultType = "SplitPhaseHumanDirectionAnswer"
  val maximumBytes = 4096

  val payloadCodec: PayloadCodec[Answer] = new PayloadCodec[Answer] {
    val typeIdentity = resultType
    def encode(value: Answer): Json = encodeC(value).toOption.getOrElse(Json.Null)
    def decode(value: Json): Either[String, Answer] = for {
      json <- Option(value).filter(v => _bounded(v.noSpaces)).toRight("Human answer is absent or oversized")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload")).toRight("Human answer fields differ")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "Human answer schema differs")
      body <- root("payload").get.asObject.toRight("Human answer body is absent")
      choice <- body("choice").flatMap(_.asObject).filter(_.keys.toSet == Set("value"))
        .flatMap(_("value")).flatMap(_.asString).toRight("Human answer choice differs")
      answer <- choice match {
        case "ProvideResolution" if body.keys.toSet == Set("choice", "resolution") => for {
          reference <- body("resolution").flatMap(_.asObject).filter(_.keys.toSet == Set("identity", "revision"))
            .toRight("Human resolution reference fields differ")
          identity <- reference("identity").flatMap(_.asString).filter(_canonical).toRight("Human resolution identity differs")
          revision <- reference("revision").flatMap(_.asString).filter(_canonical).toRight("Human resolution revision differs")
        } yield Answer.ProvideResolution(S.ContextReference(identity, revision))
        case "Defer" if body.keys.toSet == Set("choice") => Right(Answer.Defer)
        case "Cancel" if body.keys.toSet == Set("choice") => Right(Answer.Cancel)
        case _ => Left("Human answer is not a declared choice with its exact fields")
      }
    } yield answer
  }

  def encodeC(answer: Answer): Consequence[Json] = {
    val payload = answer match {
      case Answer.ProvideResolution(reference) if reference != null &&
          _canonical(reference.identity) && _canonical(reference.revision) => Some(Json.obj(
        "choice" -> _choice("ProvideResolution"), "resolution" -> Json.obj(
          "identity" -> Json.fromString(reference.identity), "revision" -> Json.fromString(reference.revision))))
      case Answer.Defer => Some(Json.obj("choice" -> _choice("Defer")))
      case Answer.Cancel => Some(Json.obj("choice" -> _choice("Cancel")))
      case _ => None
    }
    payload match {
      case Some(body) =>
        val json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> body)
        if (_bounded(json.noSpaces)) Consequence.success(json) else _invalid("payload exceeds the UTF-8 bound")
      case None => _invalid("choice or resolution reference differs")
    }
  }

  private def _choice(value: String): Json = Json.obj("value" -> Json.fromString(value))
  private def _canonical(value: String): Boolean =
    value != null && value.nonEmpty && value.length <= 1024 && value.trim == value && !value.exists(_.isControl) &&
      UTF_8.newEncoder().canEncode(value)
  private def _bounded(value: String): Boolean =
    UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= maximumBytes
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase Human answer " + reason)
}
