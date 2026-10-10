package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import NativeHostEvidence.Purpose

/** Independent Human direction for an actual issued question. No complete
  * question, Plan, diagnostic Context or answer-transport copy certificate.
  * This attestation grants neither a command nor repair/closure authority.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object NativeHostHumanScope {
  def supports(purpose: Purpose): Boolean = Set(Purpose.GoalHuman, Purpose.RepositoryHuman)(purpose)

  def select(purpose: Purpose, binding: Json, legacy: Boolean): Either[String, Json] =
    if (!supports(purpose)) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      current = root("humanAttestation").nonEmpty
      _ <- Either.cond(current || legacy, (), "Human attestation cannot retain complete question/result copies")
      _ <- shape(root, if (current) Set("componentInstance", "humanAttestation") else
        Set("componentInstance", "question", "result", "submittingParticipant"), true)
      body <- if (current) at(root, "humanAttestation") else Right(root)
      _ <- shape(body, Set("kind", "submittingParticipant", "question", "answer"), current)
      kind = if (purpose == Purpose.GoalHuman) "ordered-goal-direction" else "repository-conflict-direction"
      _ <- Either.cond(!current || body("kind").flatMap(_.asString).contains(kind), (), "Human attestation has another purpose")
      participant <- text(body, "submittingParticipant")
      rawQuestion <- at(body, "question")
      question <- questionTarget(rawQuestion, purpose, current)
      rawAnswer <- at(body, if (current) "answer" else "result")
      answer <- answerTarget(rawAnswer, question.asObject.get, purpose, current)
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "humanAttestation" -> Json.obj(
      "kind" -> Json.fromString(kind), "submittingParticipant" -> Json.fromString(participant),
      "question" -> question, "answer" -> answer))

  private def questionTarget(input: JsonObject, purpose: Purpose, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("handle", "runId", "continuationId", "expectedRevision", "requiredOperation", "operation",
      "inputType", "resultType", "completionOperation", "modelRevision", "source"), current)
    _ <- if (current) Right(()) else transport(input, "DECISION")
    handle <- at(input, "handle").flatMap(handleTarget)
    request <- if (current) Right(input) else at(input, "request")
    fields <- strings(request, Vector("runId", "continuationId", "expectedRevision", "requiredOperation", "resultType"))
    operation <- at(request, "operation").flatMap(operationTarget)
    completion <- at(request, "completionOperation").flatMap(operationTarget)
    context <- if (current) Right(input) else at(request, "context").flatMap(at(_, "snapshot"))
    model <- optionalText(context, "modelRevision")
    _ <- Either.cond(current || context("workflowRevision") == handle.asObject.get.apply("workflowRevision"), (), "Human question has another executable scope")
    typed <- if (current) Right(input) else at(request, "input")
    inputType <- text(typed, if (current) "inputType" else "typeIdentity")
    _ <- Either.cond(inputType == (if (purpose == Purpose.GoalHuman) GoalPhaseOrderedHumanDecision.inputCodec.typeIdentity
      else RepositorySyncHumanConflict.inputCodec.typeIdentity) && fields("resultType").flatMap(_.asString).contains(
        if (purpose == Purpose.GoalHuman) GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity else RepositorySyncHumanConflict.answerCodec.typeIdentity),
      (), "Human question has incompatible input/result types")
    source <- if (current) at(input, "source") else for {
      value <- at(typed, "value")
      payload <- if (purpose == Purpose.GoalHuman) Right(value) else for {
        _ <- Either.cond(value("schemaVersion").flatMap(_.asString).contains("sm-workflow.repository-human-conflict-input.v1"), (), "Human conflict input format is unsupported")
        payload <- at(value, "payload")
      } yield payload
    } yield payload
    target <- if (purpose == Purpose.GoalHuman) goalSource(source, current) else repositorySource(source, current)
    instance = if (purpose == Purpose.GoalHuman) target.hcursor.downField("assignment").get[String]("instanceIdentity")
      else target.hcursor.downField("conflict").get[String]("instanceIdentity")
    _ <- Either.cond(instance.toOption == handle.hcursor.get[String]("instanceIdentity").toOption, (), "Human question selects another native instance")
    expectedWorkflow = if (purpose == Purpose.GoalHuman) "GoalPhaseWorkflow" else "RepositorySyncWorkflow"
    expectedCompletion = if (purpose == Purpose.GoalHuman) GoalPhaseOrderedHumanDecision.completionOperation else RepositorySyncHumanConflict.completionOperation
    _ <- Either.cond(handle.hcursor.get[String]("workflowIdentity").contains(expectedWorkflow) &&
      completion.hcursor.get[String]("service").contains(expectedCompletion.service) && completion.hcursor.get[String]("operation").contains(expectedCompletion.operation),
      (), "Human question is not its actual Workflow/completion route")
  } yield Json.fromJsonObject(fields.add("handle", handle).add("operation", operation).add("completionOperation", completion)
    .add("inputType", Json.fromString(inputType)).add("modelRevision", model.map(Json.fromString).getOrElse(Json.Null)).add("source", target))

  private def goalSource(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("requestingResult", "assignment"), current)
    reference <- at(input, "requestingResult").flatMap(referenceTarget)
    work <- if (current) at(input, "assignment") else for {
      envelope <- at(input, "work")
      _ <- Either.cond(envelope("schemaVersion").flatMap(_.asString).exists(Set("sm-workflow.goal-phase-native-work-request.v1",
        "sm-workflow.goal-phase-native-work-request.v2")), (), "Human assigned work format is unsupported")
      payload <- at(envelope, "payload")
    } yield payload
    _ <- shape(work, Set("instanceIdentity", "sourceRevision", "kind", "stepIdentity", "generation", "selectedPlan", "originalBase", "candidate", "checksReceipt"), current)
    fields <- strings(work, Vector("instanceIdentity", "sourceRevision", "kind"))
    kind <- text(work, "kind")
    step <- optionalText(work, "stepIdentity")
    _ <- Either.cond(Set("ImplementStep", "RepairStep", "ReviewStep", "ReviewPhase")(kind) && (kind != "ReviewPhase") == step.nonEmpty,
      (), "Human assigned work has another Step/Phase target")
    generation <- work("generation").flatMap(_.asNumber).flatMap(_.toLong).filter(_ >= 0).toRight("Human work generation is invalid")
    plan <- at(work, "selectedPlan")
    _ <- shape(plan, Set("identity", "revision", "phase", "repository"), current)
    planFields <- strings(plan, Vector("identity", "revision", "phase", "repository"))
    base <- at(work, "originalBase").flatMap(snapshotTarget(_, current))
    candidate <- optionalObject(work, "candidate").flatMap {
      case None => Right(None)
      case Some(value) => snapshotTarget(value, current).map(Some(_))
    }
    checks <- optionalText(work, "checksReceipt")
  } yield Json.obj("requestingResult" -> reference, "assignment" -> Json.fromJsonObject(fields.add("stepIdentity", step.map(Json.fromString).getOrElse(Json.Null))
    .add("generation", Json.fromLong(generation)).add("selectedPlan", Json.fromJsonObject(planFields)).add("originalBase", base)
    .add("candidate", candidate.getOrElse(Json.Null)).add("checksReceipt", checks.map(Json.fromString).getOrElse(Json.Null))))

  private def repositorySource(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("requestingResult", "conflict"), current)
    reference <- if (current) at(input, "requestingResult").flatMap(referenceTarget) else for {
      identity <- text(input, "requestingResultIdentity")
      revision <- text(input, "requestingResultRevision")
    } yield Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
    conflict <- at(input, "conflict")
    _ <- shape(conflict, Set("instanceIdentity", "conflictRevision", "localTip", "remoteTip", "conflicts"), current)
    fields <- strings(conflict, Vector("instanceIdentity", "conflictRevision"))
    tips <- oids(conflict, Vector("localTip", "remoteTip"))
    entries <- conflict("conflicts").flatMap(_.asArray).filter(v => v.nonEmpty && v.size <= 128).toRight("Human conflict scope is absent or oversized")
    conflicts <- entries.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, value) => for {
      values <- prior
      entry <- obj(value)
      _ <- shape(entry, Set("identity", "path", "base", "local", "remote", "mode"), current)
      identity <- text(entry, "identity")
      path <- entry("path").flatMap(_.asString).filter(RepositorySyncNativeConflictInput.relative).toRight("Human conflict path is outside its native scope")
      blobs <- Vector("base", "local", "remote").foldLeft[Either[String, JsonObject]](Right(JsonObject.empty)) { (before, key) => for {
        collected <- before
        id <- if (current) text(entry, key) else at(entry, key).flatMap(text(_, "identity"))
        _ <- Either.cond(oid(id), (), "Human conflict has an invalid native blob target")
      } yield collected.add(key, Json.fromString(id)) }
      mode <- if (!current && entry("mode").isEmpty) Right("100644") else text(entry, "mode")
      _ <- Either.cond(Set("100644", "100755")(mode), (), "Human conflict has an incompatible native file mode")
    } yield values :+ Json.fromJsonObject(blobs.add("identity", Json.fromString(identity)).add("path", Json.fromString(path)).add("mode", Json.fromString(mode))) }
    _ <- Either.cond(conflicts.map(_.hcursor.get[String]("identity").toOption.get).distinct.size == conflicts.size, (), "Human conflict scope repeats native IDs")
  } yield Json.obj("requestingResult" -> reference, "conflict" -> Json.fromJsonObject(fields.add("localTip", tips("localTip").get)
    .add("remoteTip", tips("remoteTip").get).add("conflicts", Json.fromValues(conflicts))))

  private def answerTarget(input: JsonObject, question: JsonObject, purpose: Purpose, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, if (purpose == Purpose.GoalHuman) Set("reference", "worker", "choice") else Set("reference", "worker", "choice", "resolutions"), current)
    _ <- if (current) Right(()) else for {
      _ <- transport(input, "CONTINUATION_RESULT")
      handle <- at(input, "handle").flatMap(handleTarget)
      context <- at(input, "contextSnapshot")
      model <- optionalText(context, "modelRevision")
      _ <- Either.cond(handle == question("handle").get && Vector("runId", "continuationId", "expectedRevision").forall(k => input(k) == question(k)) &&
        context("workflowRevision") == handle.asObject.get.apply("workflowRevision") &&
        model.map(Json.fromString).getOrElse(Json.Null) == question("modelRevision").get, (), "Human answer belongs to another question or model scope")
    } yield ()
    payload <- if (current) Right(input) else at(input, "result")
    _ <- Either.cond(current || payload("typeIdentity") == question("resultType"), (), "Human answer result type differs")
    reference <- at(payload, "reference").flatMap(referenceTarget)
    worker <- if (current) text(input, "worker") else at(input, "executionEvidence").flatMap(text(_, "workerIdentity"))
    value <- if (current) Right(input) else for {
      raw <- at(payload, "value")
      selected <- if (purpose == Purpose.GoalHuman) Right(raw) else for {
        _ <- Either.cond(raw("schemaVersion").flatMap(_.asString).contains("sm-workflow.repository-human-conflict-answer.v1"), (), "Human conflict answer format is unsupported")
        body <- at(raw, "payload")
      } yield body
    } yield selected
    choice <- text(value, "choice")
    _ <- Either.cond((if (purpose == Purpose.GoalHuman) GoalPhaseOrderedHumanDecision.choices else RepositorySyncHumanConflict.choices).contains(choice), (), "Human choice is unsupported")
    resolutions <- if (purpose == Purpose.GoalHuman) Right(Vector.empty[Json]) else for {
      rows <- value("resolutions").flatMap(_.asArray).filter(_.size <= 128).toRight("Human resolutions are absent or oversized")
      items <- rows.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, value) => for {
        collected <- prior
        entry <- obj(value)
        _ <- shape(entry, Set("conflictIdentity", "text"), true)
        identity <- text(entry, "conflictIdentity")
        literal <- entry("text").flatMap(_.asString).filter(v => !v.contains('\u0000') &&
          v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 262144).toRight("Human resolution text is invalid or oversized")
      } yield collected :+ Json.obj("conflictIdentity" -> Json.fromString(identity), "text" -> Json.fromString(literal)) }
      ids = question("source").get.hcursor.downField("conflict").downField("conflicts").focus.get.asArray.get.map(_.hcursor.get[String]("identity").toOption.get)
      _ <- Either.cond(if (choice == "ProvideResolution") items.map(_.hcursor.get[String]("conflictIdentity").toOption.get) == ids
        else items.isEmpty, (), "Human choice does not cover exactly the assigned conflict IDs")
    } yield items
    fields = JsonObject("reference" -> reference, "worker" -> Json.fromString(worker), "choice" -> Json.fromString(choice))
  } yield Json.fromJsonObject(if (purpose == Purpose.GoalHuman) fields else fields.add("resolutions", Json.fromValues(resolutions)))

  private[admission] def bindingC[W, A](owner: String, purpose: Purpose, question: WorkflowInteraction[W, Nothing],
    answer: ContinuationResult[A], participant: String, source: Json, direction: Json): Consequence[Json] =
    if (question == null || question.handle == null || answer == null || answer.result == null || answer.evidence == null ||
        answer.resultReference == null || answer.contextSnapshot == null)
      invalid("actual Human question/answer target is absent")
    else question.current match {
      case decision: WorkflowContinuation.Decision[W] if decision.request != null =>
        val q = decision.request
        if (q.context == null || q.input.isEmpty || q.resultType.isEmpty || answer.handle != question.handle ||
            q.runId != answer.runId || q.continuationId != answer.continuationId || q.expectedRevision != answer.expectedRevision ||
            q.resultType.map(_.value) != Some(answer.result.typeIdentity) ||
            !GoalPhaseClosureAuthority.sameContextScope(q.context.snapshot, answer.contextSnapshot) || answer.evidence.workerIdentity.isEmpty)
          invalid("actual Human answer selects another question slot, type, model or participant")
        else {
          val raw = Json.obj("componentInstance" -> Json.fromString(owner), "humanAttestation" -> Json.obj(
            "kind" -> Json.fromString(if (purpose == Purpose.GoalHuman) "ordered-goal-direction" else "repository-conflict-direction"),
            "submittingParticipant" -> Json.fromString(participant), "question" -> Json.obj(
              "handle" -> handleJson(question.handle), "runId" -> Json.fromString(q.runId.value),
              "continuationId" -> Json.fromString(q.continuationId.value), "expectedRevision" -> Json.fromString(q.expectedRevision.value),
              "requiredOperation" -> Json.fromString(q.requiredOperation.capability), "operation" -> operationJson(q.operation),
              "inputType" -> Json.fromString(q.input.get.typeIdentity), "resultType" -> Json.fromString(q.resultType.get.value),
              "completionOperation" -> operationJson(q.completionOperation),
              "modelRevision" -> q.context.snapshot.modelRevision.map(Json.fromString).getOrElse(Json.Null), "source" -> source),
            "answer" -> direction.mapObject(_.add("reference", Json.obj("identity" -> Json.fromString(answer.resultReference.identity),
              "revision" -> Json.fromString(answer.resultReference.revision))).add("worker", Json.fromString(answer.evidence.workerIdentity.get)))))
          select(purpose, raw, legacy = false).fold(Consequence.stateConflict(_), Consequence.success)
        }
      case _ => invalid("actual Human question is not a Decision")
    }

  private[admission] def handleJson(value: WorkflowHandle): Json = Json.obj("componentIdentity" -> Json.fromString(value.componentIdentity.name),
    "workflowIdentity" -> Json.fromString(value.workflowIdentity.value), "workflowRevision" -> Json.fromString(value.workflowRevision.value),
    "instanceIdentity" -> Json.fromString(value.instanceIdentity.value), "protocolVersion" -> Json.fromString(value.protocolVersion))
  private[admission] def operationJson(value: StateMachineOperationIdentity): Json = Json.obj("service" -> Json.fromString(value.service), "operation" -> Json.fromString(value.operation))
  private[admission] def handleTarget(input: JsonObject): Either[String, Json] = for {
    _ <- shape(input, Set("componentIdentity", "workflowIdentity", "workflowRevision", "instanceIdentity", "protocolVersion"), true)
    fields <- strings(input, Vector("componentIdentity", "workflowIdentity", "workflowRevision", "instanceIdentity", "protocolVersion"))
    _ <- Either.cond(fields("componentIdentity").flatMap(_.asString).contains(SmWorkflowComponent.componentId.name) &&
      fields("protocolVersion").flatMap(_.asString).contains(WorkflowProtocolV1.schemaVersion), (), "Human evidence has another component/protocol")
  } yield Json.fromJsonObject(fields)
  private[admission] def operationTarget(input: JsonObject): Either[String, Json] = shape(input, Set("service", "operation"), true)
    .flatMap(_ => strings(input, Vector("service", "operation"))).map(Json.fromJsonObject)
  private[admission] def referenceTarget(input: JsonObject): Either[String, Json] = shape(input, Set("identity", "revision"), true)
    .flatMap(_ => strings(input, Vector("identity", "revision"))).map(Json.fromJsonObject)
  private def snapshotTarget(input: JsonObject, strict: Boolean): Either[String, Json] = shape(input, Set("head", "tree"), strict)
    .flatMap(_ => oids(input, Vector("head", "tree"))).map(Json.fromJsonObject)
  private def oids(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] = strings(input, keys)
    .flatMap(fields => Either.cond(keys.forall(k => fields(k).flatMap(_.asString).exists(oid)), fields, "Human native target is invalid"))
  private def oid(value: String): Boolean = value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def transport(input: JsonObject, kind: String): Either[String, Unit] = Either.cond(
    input("schemaVersion").flatMap(_.asString).contains(WorkflowProtocolV1.schemaVersion) && input("kind").flatMap(_.asString).contains(kind),
    (), "Human legacy transport is unsupported")
  private def shape(input: JsonObject, keys: Set[String], strict: Boolean): Either[String, Unit] =
    Either.cond(!strict || input.keys.toSet == keys, (), "Human attestation contains undeclared or missing fields")
  private def obj(value: Json): Either[String, JsonObject] = Option(value).flatMap(_.asObject).toRight("Human attestation must be an object")
  private def at(input: JsonObject, key: String): Either[String, JsonObject] = input(key).toRight("Human requires actual " + key).flatMap(obj)
  private def optionalObject(input: JsonObject, key: String): Either[String, Option[JsonObject]] = input(key).filterNot(_.isNull) match {
    case None => Right(None)
    case Some(value) => obj(value).map(Some(_))
  }
  private def text(input: JsonObject, key: String): Either[String, String] = input(key).flatMap(_.asString)
    .filter(v => v.nonEmpty && v.trim == v && !v.exists(_.isControl)).toRight("Human requires actual " + key)
  private def optionalText(input: JsonObject, key: String): Either[String, Option[String]] = input(key).filterNot(_.isNull) match {
    case None => Right(None)
    case Some(_) => text(input, key).map(Some(_))
  }
  private def strings(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty)) { (prior, key) => for {
      fields <- prior
      value <- text(input, key)
    } yield fields.add(key, Json.fromString(value)) }
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Human host attestation " + reason)
}
