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
  * @version Oct.  8, 2026
  */
private[smworkflow] object NativeHostSplitAnswerScope {
  def bindingC(value: AnswerContext): Consequence[Json] = {
    if (value == null || value.componentInstance == null || value.question == null || value.question.handle == null ||
        value.result == null || value.result.result == null || value.result.contextSnapshot == null || value.result.resultReference == null ||
        !name(value.submittingParticipant))
      return invalid("actual question/answer target is absent")
    value.question.current match {
      case decision: WorkflowContinuation.Decision[?] if decision.request != null =>
        val q = decision.request
        val r = value.result
        if (q.context == null || q.input == null || q.input.isEmpty || q.input.get == null || q.resultType == null || q.resultType.isEmpty ||
            q.runId == null || q.continuationId == null || q.expectedRevision == null || q.requiredOperation == null || q.operation == null ||
            q.completionOperation == null || q.context.snapshot == null || r.handle != value.question.handle || r.runId != q.runId ||
            r.continuationId != q.continuationId || r.expectedRevision != q.expectedRevision || r.result.typeIdentity != A.resultType ||
            q.resultType.get.value != A.resultType || !GoalPhaseClosureAuthority.sameContextScope(q.context.snapshot, r.contextSnapshot))
          return invalid("answer selects another slot, type or model")
        val source = q.input.get.value match {
          case input: SplitPhaseNativeHumanDirection.Input if q.input.get.typeIdentity == SplitPhaseNativeHumanDirection.inputCodec.typeIdentity &&
              input.context != null && input.checkpoint != null && input.scopeCheckpoint != null => Right(Json.obj(
            "kind" -> Json.fromString("scope"), "context" -> ref(input.context.identity, input.context.revision),
            "checkpoint" -> ref(input.checkpoint.identity, input.checkpoint.revision),
            "scopeCheckpoint" -> ref(input.scopeCheckpoint.identity, input.scopeCheckpoint.revision)))
          case input: SplitPhaseReasoningHumanDirection.Input if q.input.get.typeIdentity == SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity && input.context != null =>
            reasoningSource(ref(input.context.identity, input.context.revision), input.reasoningWork, input.reasoningResult,
              handleJson(value.question.handle))
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
              "continuationId" -> Json.fromString(q.continuationId.value), "expectedRevision" -> Json.fromString(q.expectedRevision.value),
              "requiredOperation" -> Json.fromString(q.requiredOperation.capability), "operation" -> operationJson(q.operation),
              "inputType" -> Json.fromString(q.input.get.typeIdentity), "resultType" -> Json.fromString(q.resultType.get.value),
              "completionOperation" -> operationJson(q.completionOperation), "modelRevision" -> q.context.snapshot.modelRevision.map(Json.fromString).getOrElse(Json.Null),
              "source" -> source), "answer" -> answer.mapObject(_.add("reference", ref(r.resultReference.identity, r.resultReference.revision)))))
          result <- select(Purpose.SplitAnswer, raw, legacy = false)
        } yield result
        selected.fold(Consequence.stateConflict(_), Consequence.success)
      case _ => invalid("actual question is not a Decision")
    }
  }

  def select(purpose: Purpose, binding: Json, legacy: Boolean): Either[String, Json] =
    if (purpose != Purpose.SplitAnswer) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      current = root("splitAnswerAttestation").nonEmpty
      _ <- Either.cond(current || legacy, (), "Split Human attestation cannot retain complete question/result copies")
      _ <- shape(root, if (current) Set("componentInstance", "splitAnswerAttestation") else Set("componentInstance", "question", "result", "submittingParticipant"))
      body <- if (current) at(root, "splitAnswerAttestation") else Right(root)
      _ <- if (current) shape(body, Set("submittingParticipant", "question", "answer")) else Right(())
      participant <- text(body, "submittingParticipant")
      rawQuestion <- at(body, "question")
      question <- questionTarget(rawQuestion, current)
      rawAnswer <- at(body, if (current) "answer" else "result")
      answer <- answerTarget(rawAnswer, question.asObject.get, current)
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "splitAnswerAttestation" -> Json.obj(
      "submittingParticipant" -> Json.fromString(participant), "question" -> question, "answer" -> answer))

  /** Actual original assignment/result scope shared with issued-question
    * resolution, not complete WorkOrder/Result/frontier certification.
    */
  def reasoningTargetC(input: SplitPhaseReasoningHumanDirection.Input, handle: WorkflowHandle): Consequence[Json] =
    if (input == null || input.context == null || handle == null) invalid("reasoning question target is absent")
    else reasoningSource(ref(input.context.identity, input.context.revision), input.reasoningWork, input.reasoningResult, handleJson(handle))
      .fold(Consequence.stateConflict(_), Consequence.success)

  private def questionTarget(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- if (current) shape(input, Set("handle", "runId", "continuationId", "expectedRevision", "requiredOperation", "operation",
      "inputType", "resultType", "completionOperation", "modelRevision", "source")) else transport(input, "DECISION")
    handle <- at(input, "handle").flatMap(handleTarget)
    request <- if (current) Right(input) else at(input, "request")
    fields <- strings(request, Vector("runId", "continuationId", "expectedRevision", "requiredOperation", "resultType"))
    operation <- at(request, "operation").flatMap(operationTarget)
    completion <- at(request, "completionOperation").flatMap(operationTarget)
    context <- if (current) Right(input) else at(request, "context").flatMap(at(_, "snapshot"))
    model <- optionalText(context, "modelRevision")
    _ <- Either.cond(current || context("workflowRevision") == handle.asObject.get.apply("workflowRevision"), (), "Split question executable scope differs")
    typed <- if (current) Right(input) else at(request, "input")
    inputType <- text(typed, if (current) "inputType" else "typeIdentity")
    _ <- Either.cond(Set(SplitPhaseNativeHumanDirection.inputCodec.typeIdentity, SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity)(inputType) &&
      fields("resultType").flatMap(_.asString).contains(A.resultType) && handle.hcursor.get[String]("workflowIdentity").contains("SplitPhaseWorkflow") &&
      completion == operationJson(SplitPhaseNativeHumanDirection.completionOperation), (), "Split Human types or completion route are incompatible")
    source <- if (current) at(input, "source").flatMap(sourceTarget(_, inputType)) else for {
      value <- at(typed, "value")
      reasoning = inputType == SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity
      _ <- Either.cond(value("schemaVersion").flatMap(_.asString).contains(if (reasoning) SplitPhaseReasoningHumanDirection.schema
        else SplitPhaseNativeHumanDirection.inputSchema), (), "Split Human input format is unsupported")
      payload <- at(value, "payload")
      context <- at(payload, "context").flatMap(referenceTarget)
      source <- if (reasoning) for {
        work <- payload("reasoningWork").flatMap(_.asString).toRight("Split reasoning work transport is absent")
        result <- payload("reasoningResult").flatMap(_.asString).toRight("Split reasoning result transport is absent")
        selected <- reasoningSource(context, work, result, handle)
      } yield selected else for {
        checkpoint <- at(payload, "checkpoint").flatMap(referenceTarget)
        scope <- at(payload, "scopeCheckpoint").flatMap(referenceTarget)
      } yield Json.obj("kind" -> Json.fromString("scope"), "context" -> context, "checkpoint" -> checkpoint, "scopeCheckpoint" -> scope)
    } yield source
  } yield Json.fromJsonObject(fields.add("handle", handle).add("operation", operation).add("completionOperation", completion)
    .add("inputType", Json.fromString(inputType)).add("modelRevision", model.map(Json.fromString).getOrElse(Json.Null)).add("source", source))

  private def sourceTarget(value: JsonObject, inputType: String): Either[String, Json] = for {
    kind <- text(value, "kind")
    reasoning = inputType == SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity
    _ <- Either.cond(kind == (if (reasoning) "reasoning" else "scope"), (), "Split question source kind differs")
    _ <- shape(value, if (reasoning) Set("kind", "context", "work", "result") else Set("kind", "context", "checkpoint", "scopeCheckpoint"))
    context <- at(value, "context").flatMap(referenceTarget)
    selected <- if (!reasoning) for {
      checkpoint <- at(value, "checkpoint").flatMap(referenceTarget)
      scope <- at(value, "scopeCheckpoint").flatMap(referenceTarget)
    } yield Json.obj("kind" -> Json.fromString(kind), "context" -> context, "checkpoint" -> checkpoint, "scopeCheckpoint" -> scope) else for {
      work <- at(value, "work")
      _ <- shape(work, Set("continuationId", "expectedRevision", "source", "authority", "demand", "items"))
      fields <- strings(work, Vector("continuationId", "expectedRevision"))
      source <- at(work, "source").flatMap(referenceTarget)
      authority <- at(work, "authority").flatMap(referenceTarget)
      demand <- at(work, "demand").flatMap(referenceTarget)
      items <- work("items").flatMap(_.asArray).toRight("Split reasoning assignment has no actual items")
      ids <- items.foldLeft[Either[String, Vector[String]]](Right(Vector.empty))((prior, item) => for {
        collected <- prior
        id <- item.asString.filter(name).toRight("Split reasoning item target is invalid")
      } yield collected :+ id)
      _ <- Either.cond(ids.nonEmpty && ids.distinct.size == ids.size, (), "Split reasoning item target is empty or repeated")
      result <- at(value, "result").flatMap(referenceTarget)
    } yield Json.obj("kind" -> Json.fromString(kind), "context" -> context, "result" -> result, "work" -> Json.fromJsonObject(fields
      .add("source", source).add("authority", authority).add("demand", demand).add("items", Json.fromValues(ids.map(Json.fromString)))))
  } yield selected

  private def reasoningSource(context: Json, workRaw: String, resultRaw: String, handle: Json): Either[String, Json] = for {
    work <- parsed(workRaw)
    result <- parsed(resultRaw)
    _ <- transport(work, "WORK_ORDER")
    _ <- transport(result, "CONTINUATION_RESULT")
    workHandle <- at(work, "handle").flatMap(handleTarget)
    resultHandle <- at(result, "handle").flatMap(handleTarget)
    request <- at(work, "request")
    workContext <- at(request, "context").flatMap(at(_, "snapshot"))
    resultContext <- at(result, "contextSnapshot")
    model <- optionalText(workContext, "modelRevision")
    resultModel <- optionalText(resultContext, "modelRevision")
    _ <- Either.cond(workHandle == handle && resultHandle == handle && Vector("runId", "continuationId", "expectedRevision").forall(k => request(k).nonEmpty && request(k) == result(k)) &&
      workContext("workflowRevision") == handle.asObject.get.apply("workflowRevision") && resultContext("workflowRevision") == workContext("workflowRevision") && model == resultModel,
      (), "Split reasoning source has another native work/result slot or model")
    typed <- at(request, "input")
    payload <- at(typed, "value")
    _ <- Either.cond(typed("typeIdentity").flatMap(_.asString).contains(SplitPhaseExplicitReasoningSemanticJson.requestCodec.typeIdentity) &&
      payload("schemaVersion").flatMap(_.asString).contains(SplitPhaseExplicitReasoningSemanticJson.requestCodec.schema), (), "Split reasoning source input is incompatible")
    input <- at(payload, "payload")
    originalContext <- at(input, "context").flatMap(referenceTarget)
    _ <- Either.cond(originalContext == context, (), "Split reasoning source selects another original context")
    fields <- strings(request, Vector("continuationId", "expectedRevision"))
    source <- at(input, "source").flatMap(referenceTarget)
    authority <- at(input, "authority").flatMap(referenceTarget)
    demand <- at(input, "demand").flatMap(referenceTarget)
    items <- input("items").flatMap(_.asArray).toRight("Split reasoning source has no actual items")
    ids <- items.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty))((prior, row) => for {
      collected <- prior
      item <- obj(row)
      id <- at(item, "identity").flatMap(text(_, "value"))
    } yield collected :+ Json.fromString(id))
    answer <- at(result, "result")
    reference <- at(answer, "reference").flatMap(referenceTarget)
    assigned <- at(input, "result").flatMap(referenceTarget)
    _ <- Either.cond(reference == assigned && answer("typeIdentity") == request("resultType") &&
      answer("typeIdentity").flatMap(_.asString).contains(SplitPhaseExplicitReasoningSemanticJson.resultCodec.typeIdentity), (), "Split reasoning source result target differs")
    raw = Json.obj("kind" -> Json.fromString("reasoning"), "context" -> context, "result" -> reference, "work" -> Json.fromJsonObject(fields
      .add("source", source).add("authority", authority).add("demand", demand).add("items", Json.fromValues(ids))))
    selected <- sourceTarget(raw.asObject.get, SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity)
  } yield selected

  private def answerTarget(input: JsonObject, question: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- if (current) shape(input, Set("reference", "choice", "resolution")) else for {
      _ <- transport(input, "CONTINUATION_RESULT")
      handle <- at(input, "handle").flatMap(handleTarget)
      context <- at(input, "contextSnapshot")
      model <- optionalText(context, "modelRevision")
      _ <- Either.cond(handle == question("handle").get && Vector("runId", "continuationId", "expectedRevision").forall(k => input(k) == question(k)) &&
        context("workflowRevision") == handle.asObject.get.apply("workflowRevision") && model.map(Json.fromString).getOrElse(Json.Null) == question("modelRevision").get,
        (), "Split Human answer belongs to another question/model")
    } yield ()
    result <- if (current) Right(input) else at(input, "result")
    _ <- Either.cond(current || result("typeIdentity").flatMap(_.asString).contains(A.resultType), (), "Split Human result type differs")
    reference <- at(result, "reference").flatMap(referenceTarget)
    value <- if (current) Right(input) else for {
      envelope <- at(result, "value")
      _ <- Either.cond(envelope("schemaVersion").flatMap(_.asString).contains(A.schema), (), "Split Human answer format differs")
      body <- at(envelope, "payload")
    } yield body
    choice <- if (current) text(value, "choice") else at(value, "choice").flatMap(text(_, "value"))
    _ <- Either.cond(A.choices.contains(choice), (), "Split Human direction is unsupported")
    resolution <- if (choice == "ProvideResolution") at(value, "resolution").flatMap(referenceTarget) else {
      Either.cond(if (current) value("resolution").contains(Json.Null) else value("resolution").isEmpty,
        Json.Null, "Stopped Split Human direction cannot carry a resolution")
    }
  } yield Json.obj("reference" -> reference, "choice" -> Json.fromString(choice), "resolution" -> resolution)

  private def ref(identity: String, revision: String): Json = Json.obj("identity" -> Option(identity).map(Json.fromString).getOrElse(Json.Null),
    "revision" -> Option(revision).map(Json.fromString).getOrElse(Json.Null))
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def obj(value: Json): Either[String, JsonObject] = Option(value).flatMap(_.asObject).toRight("Split Human scope must be an object")
  private def at(value: JsonObject, key: String): Either[String, JsonObject] = value(key).toRight("Split Human scope lacks " + key).flatMap(obj)
  private def text(value: JsonObject, key: String): Either[String, String] = value(key).flatMap(_.asString).filter(name).toRight("Split Human scope lacks actual " + key)
  private def optionalText(value: JsonObject, key: String): Either[String, Option[String]] = value(key).filterNot(_.isNull) match {
    case None => Right(None)
    case Some(_) => text(value, key).map(Some(_))
  }
  private def shape(value: JsonObject, keys: Set[String]): Either[String, Unit] = Either.cond(value.keys.toSet == keys, (), "Split Human scope has undeclared or missing fields")
  private def strings(value: JsonObject, keys: Vector[String]): Either[String, JsonObject] = keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty))((prior, key) => for {
    collected <- prior
    field <- text(value, key)
  } yield collected.add(key, Json.fromString(field)))
  private def transport(value: JsonObject, kind: String): Either[String, Unit] = Either.cond(value("schemaVersion").flatMap(_.asString).contains(WorkflowProtocolV1.schemaVersion) &&
    value("kind").flatMap(_.asString).contains(kind), (), "Split Human legacy transport differs")
  private def parsed(raw: String): Either[String, JsonObject] = Option(raw).filter(v => v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 1024 * 1024)
    .toRight("Split reasoning target envelope is absent or oversized").flatMap(v => parser.parse(v).left.map(_.message)).flatMap(obj)
  private def invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("Split Human attestation " + reason)
}
