package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import NativeHostEvidence.Purpose
import NativeHostHumanScope.{handleTarget, operationTarget, referenceTarget}
import SplitPhaseHumanAnswerVerification.AnswerContext
import SplitPhaseHumanDirectionAnswerContract as A

/** Independent direction for the issued Scope/Reasoning question, not a
  * question/frontier/Result transport copy certificate. Original issued meaning
  * and resolution interpretation remain owned by their Workflow records.
  * @since   Oct.  8, 2026
  * @version Oct. 14, 2026
  */
private[smworkflow] object NativeHostSplitAnswerScope {
  def sourceC(question: WorkflowInteraction[?, Nothing]): Consequence[Json] = {
    if (question == null) return invalid("actual question is absent")
    question.current match {
      case decision: WorkflowContinuation.Decision[?] if decision.request != null && decision.request.input != null &&
          decision.request.input.exists(_ != null) =>
        val typed = decision.request.input.get
        val selected = typed.value match {
          case input: SplitPhaseNativeHumanDirection.Input if typed.typeIdentity == SplitPhaseNativeHumanDirection.inputCodec.typeIdentity &&
              input.context != null && input.checkpoint != null && input.scopeCheckpoint != null &&
              Vector(input.context.identity, input.context.revision, input.checkpoint.identity, input.checkpoint.revision,
                input.scopeCheckpoint.identity, input.scopeCheckpoint.revision).forall(_ != null) => Right(Json.obj(
            "kind" -> Json.fromString("scope"), "context" -> ref(input.context.identity, input.context.revision),
            "checkpoint" -> ref(input.checkpoint.identity, input.checkpoint.revision),
            "scopeCheckpoint" -> ref(input.scopeCheckpoint.identity, input.scopeCheckpoint.revision)))
          case input: SplitPhaseReasoningHumanDirection.Input if typed.typeIdentity == SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity && input.request != null =>
            reasoningSource(input)
          case _ => Left("Split Human evidence has no actual Scope or Reasoning target")
        }
        selected.fold(Consequence.stateConflict(_), Consequence.success)
      case _ => invalid("actual question is not a typed Decision")
    }
  }

  def questionBindingC(question: WorkflowInteraction[?, Nothing]): Consequence[Json] = for {
    source <- sourceC(question)
    selected <- NativeHostHumanScope.questionBindingC(question, source)
  } yield selected

  def binding(owner: String, participant: String, questionJson: Json, answerJson: Json): Json =
    Json.obj("componentInstance" -> Json.fromString(owner), "splitAnswerAttestation" -> Json.obj(
      "submittingParticipant" -> Json.fromString(participant), "question" -> questionJson, "answer" -> answerJson))

  def bindingC(value: AnswerContext): Consequence[Json] = {
    if (value == null || value.componentInstance == null || value.result == null || value.result.result == null ||
        value.result.resultReference == null) return invalid("actual question/answer target is absent")
    val r = value.result
    val direction = r.result.value match {
      case A.Answer.ProvideResolution(reference) if reference != null => Right(Json.obj("choice" -> Json.fromString("ProvideResolution"),
        "resolution" -> ref(reference.identity, reference.revision)))
      case A.Answer.Defer => Right(Json.obj("choice" -> Json.fromString("Defer"), "resolution" -> Json.Null))
      case A.Answer.Cancel => Right(Json.obj("choice" -> Json.fromString("Cancel"), "resolution" -> Json.Null))
      case _ => Left("Split Human answer has no actual direction")
    }
    for {
      question <- questionBindingC(value.question)
      answer <- direction.fold(Consequence.stateConflict(_), Consequence.success)
    } yield binding(value.componentInstance.canonicalKey, value.submittingParticipant, question,
      answer.mapObject(_.add("reference", ref(r.resultReference.identity, r.resultReference.revision))))
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
    else reasoningSource(input)
      .fold(Consequence.stateConflict(_), Consequence.success)

  private[smworkflow] def selectQuestion(question: Json): Either[String, Json] = obj(question).flatMap(questionTarget)

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

  private def reasoningSource(input: SplitPhaseReasoningHumanDirection.Input): Either[String, Json] =
    if (input == null || input.request == null || input.workIdentity == null || input.resultReference == null || input.request.context == null || input.request.source == null ||
        input.request.authority == null || input.request.demand == null || input.request.items == null ||
        input.request.items.exists(item => item == null || item.identity == null || item.identity.value == null) ||
        Vector(input.request.context.identity, input.request.context.revision, input.request.source.identity, input.request.source.revision,
          input.request.authority.identity, input.request.authority.revision, input.request.demand.identity, input.request.demand.revision,
          input.resultReference.identity, input.resultReference.revision, input.workIdentity.value).exists(_ == null))
      Left("Split reasoning question target is absent")
    else {
      val request = input.request
      Right(Json.obj("kind" -> Json.fromString("reasoning"),
        "context" -> ref(request.context.identity, request.context.revision),
        "result" -> ref(input.resultReference.identity, input.resultReference.revision),
        "work" -> Json.obj("continuationId" -> Json.fromString(input.workIdentity.value),
          "source" -> ref(request.source.identity, request.source.revision),
          "authority" -> ref(request.authority.identity, request.authority.revision),
          "demand" -> ref(request.demand.identity, request.demand.revision),
          "items" -> Json.fromValues(request.items.map(item => Json.fromString(item.identity.value))))))
    }

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
  private def invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("Split Human attestation " + reason)
}
