package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject, parser}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import NativeHostEvidence.Purpose
import NativeHostHumanScope.{handleJson, operationJson, handleTarget, operationTarget, referenceTarget}
import SplitPhaseHumanAnswerVerification.AnswerContext
import SplitPhaseHumanDirectionAnswerContract as A

/** Independent direction for the issued Scope/Reasoning question, not a
  * question/frontier/Result transport copy certificate. Original issued meaning
  * and resolution interpretation remain owned by their Workflow records.
  * @since   Oct.  8, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object NativeHostSplitAnswerScope {
  def bindingC(value: AnswerContext): Consequence[Json] = {
    if (value == null || value.componentInstance == null || value.question == null || value.question.handle == null ||
        value.result == null || value.result.result == null || value.result.resultReference == null)
      return invalid("actual question/answer target is absent")
    value.question.current match {
      case decision: WorkflowContinuation.Decision[?] if decision.request != null =>
        val q = decision.request
        val r = value.result
        if (q.input == null || q.input.isEmpty || q.input.get == null || q.resultType == null || q.resultType.isEmpty)
          return invalid("typed question input/result is absent")
        val source = q.input.get.value match {
          case input: SplitPhaseNativeHumanDirection.Input if q.input.get.typeIdentity == SplitPhaseNativeHumanDirection.inputCodec.typeIdentity &&
              input.context != null && input.checkpoint != null && input.scopeCheckpoint != null => Right(Json.obj(
            "kind" -> Json.fromString("scope"), "context" -> ref(input.context.identity, input.context.revision),
            "checkpoint" -> ref(input.checkpoint.identity, input.checkpoint.revision),
            "scopeCheckpoint" -> ref(input.scopeCheckpoint.identity, input.scopeCheckpoint.revision)))
          case input: SplitPhaseReasoningHumanDirection.Input if q.input.get.typeIdentity == SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity && input.context != null =>
            reasoningSource(ref(input.context.identity, input.context.revision), input.reasoningWork, input.reasoningResult)
          case _ => Left("Split Human evidence has no actual Scope or Reasoning target")
        }
        val direction = r.result.value match {
          case A.Answer.ProvideResolution(reference) if reference != null => Right(Json.obj("choice" -> Json.fromString("ProvideResolution"),
            "resolution" -> ref(reference.identity, reference.revision)))
          case A.Answer.Defer => Right(Json.obj("choice" -> Json.fromString("Defer"), "resolution" -> Json.Null))
          case A.Answer.Cancel => Right(Json.obj("choice" -> Json.fromString("Cancel"), "resolution" -> Json.Null))
          case _ => Left("Split Human answer has no actual direction")
        }
        val selected = for {
          source <- source
          answer <- direction
          raw = Json.obj("componentInstance" -> Json.fromString(value.componentInstance.canonicalKey), "splitAnswerAttestation" -> Json.obj(
            "submittingParticipant" -> Json.fromString(value.submittingParticipant), "question" -> Json.obj(
              "handle" -> handleJson(value.question.handle), "runId" -> Json.fromString(q.runId.value),
              "continuationId" -> Json.fromString(q.continuationId.value),
              "requiredOperation" -> Json.fromString(q.requiredOperation.capability), "operation" -> operationJson(q.operation),
              "inputType" -> Json.fromString(q.input.get.typeIdentity), "resultType" -> Json.fromString(q.resultType.get.value),
              "completionOperation" -> operationJson(q.completionOperation),
              "source" -> source), "answer" -> answer.mapObject(_.add("reference", ref(r.resultReference.identity, r.resultReference.revision)))))
        } yield raw
        selected.fold(Consequence.stateConflict(_), Consequence.success)
      case _ => invalid("actual question is not a Decision")
    }
  }

  def select(purpose: Purpose, binding: Json): Either[String, Json] =
    if (purpose != Purpose.SplitAnswer) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      body <- at(root, "splitAnswerAttestation")
      participant <- text(body, "submittingParticipant")
      question <- at(body, "question").flatMap(questionTarget)
      answer <- at(body, "answer").flatMap(answerTarget)
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "splitAnswerAttestation" -> Json.obj(
      "submittingParticipant" -> Json.fromString(participant), "question" -> question, "answer" -> answer))

  // Kept signature for the native issued-source consumer, not a handle certificate.
  def reasoningTargetC(input: SplitPhaseReasoningHumanDirection.Input, handle: WorkflowHandle): Consequence[Json] =
    if (input == null || input.context == null) invalid("reasoning question target is absent")
    else reasoningSource(ref(input.context.identity, input.context.revision), input.reasoningWork, input.reasoningResult)
      .fold(Consequence.stateConflict(_), Consequence.success)

  private def questionTarget(input: JsonObject): Either[String, Json] = for {
    fields <- strings(input, Vector("runId", "continuationId", "requiredOperation", "inputType", "resultType"))
    handle <- at(input, "handle").flatMap(handleTarget)
    operation <- at(input, "operation").flatMap(operationTarget)
    completion <- at(input, "completionOperation").flatMap(operationTarget)
    inputType <- text(input, "inputType")
    source <- at(input, "source").flatMap(sourceTarget(_, inputType))
  } yield Json.fromJsonObject(fields.add("handle", handle).add("operation", operation).add("completionOperation", completion).add("source", source))

  private def sourceTarget(value: JsonObject, inputType: String): Either[String, Json] = for {
    context <- at(value, "context").flatMap(referenceTarget)
    selected <- if (inputType != SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity) for {
      checkpoint <- at(value, "checkpoint").flatMap(referenceTarget)
      scope <- at(value, "scopeCheckpoint").flatMap(referenceTarget)
    } yield Json.obj("kind" -> Json.fromString("scope"), "context" -> context, "checkpoint" -> checkpoint, "scopeCheckpoint" -> scope)
    else for {
      work <- at(value, "work")
      fields <- strings(work, Vector("continuationId"))
      source <- at(work, "source").flatMap(referenceTarget)
      authority <- at(work, "authority").flatMap(referenceTarget)
      demand <- at(work, "demand").flatMap(referenceTarget)
      items <- work("items").flatMap(_.asArray).toRight("Split reasoning items must be an array")
      ids <- items.foldLeft[Either[String, Vector[String]]](Right(Vector.empty))((prior, item) => for {
        values <- prior
        id <- item.asString.toRight("Split reasoning items must be Strings")
      } yield values :+ id)
      result <- at(value, "result").flatMap(referenceTarget)
    } yield Json.obj("kind" -> Json.fromString("reasoning"), "context" -> context, "result" -> result, "work" -> Json.fromJsonObject(fields
      .add("source", source).add("authority", authority).add("demand", demand).add("items", Json.fromValues(ids.map(Json.fromString)))))
  } yield selected

  private def reasoningSource(context: Json, workRaw: String, resultRaw: String): Either[String, Json] = for {
    work <- parsed(workRaw)
    result <- parsed(resultRaw)
    request <- at(work, "request")
    typed <- at(request, "input")
    payload <- at(typed, "value")
    input <- at(payload, "payload")
    fields <- strings(request, Vector("continuationId"))
    source <- at(input, "source").flatMap(referenceTarget)
    authority <- at(input, "authority").flatMap(referenceTarget)
    demand <- at(input, "demand").flatMap(referenceTarget)
    items <- input("items").flatMap(_.asArray).toRight("Split reasoning items must be an array")
    ids <- items.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty))((prior, row) => for {
      values <- prior
      item <- obj(row)
      id <- at(item, "identity").flatMap(text(_, "value"))
    } yield values :+ Json.fromString(id))
    reference <- at(result, "result").flatMap(at(_, "reference")).flatMap(referenceTarget)
  } yield Json.obj("kind" -> Json.fromString("reasoning"), "context" -> context, "result" -> reference, "work" -> Json.fromJsonObject(fields
    .add("source", source).add("authority", authority).add("demand", demand).add("items", Json.fromValues(ids))))

  private def answerTarget(input: JsonObject): Either[String, Json] = for {
    reference <- at(input, "reference").flatMap(referenceTarget)
    choice <- text(input, "choice")
    resolution <- if (choice == "ProvideResolution") at(input, "resolution").flatMap(referenceTarget) else Right(Json.Null)
  } yield Json.obj("reference" -> reference, "choice" -> Json.fromString(choice), "resolution" -> resolution)

  private def ref(identity: String, revision: String): Json = Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
  private def obj(value: Json): Either[String, JsonObject] = Option(value).flatMap(_.asObject).toRight("Split Human scope must be an object")
  private def at(value: JsonObject, key: String): Either[String, JsonObject] = value(key).toRight("Split Human requires " + key).flatMap(obj)
  private def text(value: JsonObject, key: String): Either[String, String] = value(key).flatMap(_.asString).toRight("Split Human requires String " + key)
  private def strings(value: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty))((prior, key) => for {
      fields <- prior
      value <- text(value, key)
    } yield fields.add(key, Json.fromString(value)))
  private def parsed(raw: String): Either[String, JsonObject] =
    Option(raw).toRight("Split reasoning target is absent").flatMap(v => parser.parse(v).left.map(_.message)).flatMap(obj)
  private def invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("Split Human attestation " + reason)
}
