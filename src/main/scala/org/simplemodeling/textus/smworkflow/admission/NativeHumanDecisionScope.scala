package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import NativeExternalEvidence.Purpose

/** Selected Human direction, not a question/result copy certificate.
  * Native completion owns live state, result type and CAS.
  * @since   Oct.  8, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeHumanDecisionScope {
  def supports(purpose: Purpose): Boolean = Set(Purpose.GoalHuman, Purpose.RepositoryHuman)(purpose)
  def select(purpose: Purpose, binding: Json): Either[String, Json] =
    if (!supports(purpose)) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      body <- at(root, "humanAttestation")
      participant <- text(body, "submittingParticipant")
      question <- at(body, "question").flatMap(questionTarget(_, purpose))
      answer <- at(body, "answer").flatMap(answerTarget(_, purpose))
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "humanAttestation" -> Json.obj(
      "kind" -> Json.fromString(if (purpose == Purpose.GoalHuman) "ordered-goal-direction" else "repository-conflict-direction"),
      "submittingParticipant" -> Json.fromString(participant), "question" -> question, "answer" -> answer))

  private[smworkflow] def selectQuestion(question: Json, purpose: Purpose): Either[String, Json] =
    obj(question).flatMap(questionTarget(_, purpose))

  private def questionTarget(input: JsonObject, purpose: Purpose): Either[String, Json] = for {
    fields <- strings(input, Vector("runId", "continuationId", "requiredOperation", "inputType", "resultType"))
    handle <- at(input, "handle").flatMap(handleTarget)
    operation <- at(input, "operation").flatMap(operationTarget)
    completion <- at(input, "completionOperation").flatMap(operationTarget)
    source <- at(input, "source")
    target <- if (purpose == Purpose.GoalHuman) goalSource(source) else repositorySource(source)
  } yield Json.fromJsonObject(fields.add("handle", handle).add("operation", operation).add("completionOperation", completion).add("source", target))

  private def goalSource(input: JsonObject): Either[String, Json] = for {
    reference <- at(input, "requestingResult").flatMap(referenceTarget)
    work <- at(input, "assignment")
    fields <- strings(work, Vector("instanceIdentity", "sourceRevision", "kind"))
    step <- optionalText(work, "stepIdentity")
    generation <- work("generation").flatMap(_.asNumber).flatMap(_.toLong).toRight("Human generation must be an integer")
    plan <- at(work, "selectedPlan").flatMap(strings(_, Vector("identity", "revision", "phase", "repository")))
    base <- at(work, "originalBase").flatMap(snapshotTarget)
    candidate <- optionalObject(work, "candidate").flatMap {
      case None => Right(Json.Null)
      case Some(value) => snapshotTarget(value)
    }
    checks <- optionalText(work, "checksReceipt")
    currentprogress <- GoalPhaseRepairProgress.selectOption(input)
    assignedprogress <- GoalPhaseRepairProgress.selectOption(work)
    observations <- input("result") match {
      case None => Right(None)
      case Some(value) => for {
        result <- obj(value)
        _ <- Either.cond(result.nonEmpty && result.keys.forall(Set("reviewObservations", "dependencyProposal")), (),
          "Human result requires exact nonempty observations or dependency proposal fields")
        observed <- result("reviewObservations") match {
          case None => Right(None)
          case Some(value) => GoalPhaseFindingRecording.normalizeObservations(value).map(Some(_))
        }
        dependency <- result("dependencyProposal") match {
          case None => Right(None)
          case Some(value) => value.as[GoalPhaseDependencyScope.Proposal].left.map(_.message)
            .map(value => Some(summon[io.circe.Encoder[GoalPhaseDependencyScope.Proposal]].apply(value)))
        }
        selected = observed.fold(JsonObject.empty)(value => JsonObject("reviewObservations" -> value))
      } yield Some(Json.fromJsonObject(dependency.fold(selected)(value => selected.add("dependencyProposal", value))))
    }
    revision <- input("planRevision") match {
      case None => Right(None)
      case Some(value) => value.as[GoalPhasePlanRevision.Captured].left.map(_.message)
        .map(value => Some(summon[io.circe.Encoder[GoalPhasePlanRevision.Captured]].apply(value)))
    }
    recovery <- input("sourceHistoryRecovery") match {
      case None => Right(None)
      case Some(value) => GoalPhaseSourceHistoryRecovery.decodeTarget(value)
        .map(value => Some(summon[io.circe.Encoder[GoalPhaseSourceHistoryRecovery.RecoveryTarget]].apply(value)))
    }
    assigned = fields.add("stepIdentity", step.map(Json.fromString).getOrElse(Json.Null))
      .add("generation", Json.fromLong(generation)).add("selectedPlan", Json.fromJsonObject(plan))
      .add("originalBase", base).add("candidate", candidate)
      .add("checksReceipt", checks.map(Json.fromString).getOrElse(Json.Null))
    selected = Json.obj("requestingResult" -> reference, "assignment" -> Json.fromJsonObject(
      assignedprogress.fold(assigned)(value => assigned.add("repairProgress", value))))
  } yield selected.mapObject { fields =>
    val progress = currentprogress.fold(fields)(value => fields.add("repairProgress", value))
    val revised = revision.fold(progress)(value => progress.add("planRevision", value))
    val recovered = recovery.fold(revised)(value => revised.add("sourceHistoryRecovery", value))
    observations.fold(recovered)(value => recovered.add("result", value))
  }

  private def repositorySource(input: JsonObject): Either[String, Json] = for {
    reference <- at(input, "requestingResult").flatMap(referenceTarget)
    conflict <- at(input, "conflict")
    fields <- strings(conflict, Vector("instanceIdentity", "conflictRevision", "localTip", "remoteTip"))
    entries <- conflict("conflicts").flatMap(_.asArray).toRight("Human conflicts must be an array")
    conflicts <- entries.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty))((prior, value) => for {
      values <- prior
      entry <- obj(value)
      fields <- strings(entry, Vector("identity", "path", "base", "local", "remote", "mode"))
    } yield values :+ Json.fromJsonObject(fields))
  } yield Json.obj("requestingResult" -> reference, "conflict" -> Json.fromJsonObject(fields.add("conflicts", Json.fromValues(conflicts))))

  private def answerTarget(input: JsonObject, purpose: Purpose): Either[String, Json] = for {
    reference <- at(input, "reference").flatMap(referenceTarget)
    fields <- strings(input, Vector("worker", "choice"))
    choice <- text(input, "choice")
    resolutions <- if (purpose != Purpose.RepositoryHuman || choice != "ProvideResolution") Right(Vector.empty[Json])
      else input("resolutions").flatMap(_.asArray).toRight("Human resolutions must be an array").flatMap(
        _.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty))((prior, value) => for {
          values <- prior
          entry <- obj(value)
          fields <- strings(entry, Vector("conflictIdentity", "text"))
        } yield values :+ Json.fromJsonObject(fields)))
    answer = fields.add("reference", reference)
  } yield Json.fromJsonObject(if (purpose == Purpose.GoalHuman) answer else answer.add("resolutions", Json.fromValues(resolutions)))

  private[smworkflow] def questionBindingC[W](question: WorkflowInteraction[W, Nothing], source: Json): Consequence[Json] = {
    if (question == null || question.handle == null || source == null) {
      return invalid("actual Human question target is absent")
    }
    question.current match {
      case decision: WorkflowContinuation.Decision[W] if decision.request != null =>
        val q = decision.request
        if (q.input == null || q.input.isEmpty || q.input.get == null || q.resultType == null ||
            q.resultType.isEmpty || q.resultType.get == null || q.runId == null || q.continuationId == null ||
            q.requiredOperation == null || q.operation == null || q.completionOperation == null ||
            question.handle.componentIdentity == null || question.handle.workflowIdentity == null ||
            question.handle.workflowRevision == null || question.handle.instanceIdentity == null) {
          invalid("actual Human question has no typed input/result or completion identity")
        } else if (!Vector(q.runId.value, q.continuationId.value, q.requiredOperation.capability, q.input.get.typeIdentity,
            q.resultType.get.value, q.operation.service, q.operation.operation, q.completionOperation.service,
            q.completionOperation.operation, question.handle.componentIdentity.name, question.handle.workflowIdentity.value,
            question.handle.workflowRevision.value, question.handle.instanceIdentity.value, question.handle.protocolVersion).forall(_exact)) {
          invalid("actual Human question has an incomplete selected identity")
        } else {
          val selected = Json.obj("handle" -> handleJson(question.handle), "runId" -> Json.fromString(q.runId.value),
            "continuationId" -> Json.fromString(q.continuationId.value), "requiredOperation" -> Json.fromString(q.requiredOperation.capability),
            "operation" -> operationJson(q.operation), "inputType" -> Json.fromString(q.input.get.typeIdentity),
            "resultType" -> Json.fromString(q.resultType.get.value), "completionOperation" -> operationJson(q.completionOperation), "source" -> source)
          if (selected.asObject.exists(o => Vector("runId", "continuationId", "requiredOperation", "inputType", "resultType")
              .forall(k => o(k).flatMap(_.asString).exists(_exact))) &&
              selected.hcursor.downField("handle").focus.flatMap(_.asObject).exists(_.values.forall(_.asString.exists(_exact))) &&
              Vector(q.operation.service, q.operation.operation, q.completionOperation.service, q.completionOperation.operation).forall(_exact)) {
            Consequence.success(selected)
          } else invalid("actual Human question has an incomplete selected identity")
        }
      case _ => invalid("actual Human question is not a Decision with typed input/result")
    }
  }

  private[smworkflow] def humanBinding(owner: String, purpose: Purpose, participant: String,
    questionJson: Json, answerJson: Json): Json = Json.obj("componentInstance" -> Json.fromString(owner),
    "humanAttestation" -> Json.obj("kind" -> Json.fromString(if (purpose == Purpose.GoalHuman)
      "ordered-goal-direction" else "repository-conflict-direction"), "submittingParticipant" -> Json.fromString(participant),
      "question" -> questionJson, "answer" -> answerJson))

  private[admission] def bindingC[W, A](owner: String, purpose: Purpose, question: WorkflowInteraction[W, Nothing],
    answer: ContinuationResult[A], participant: String, source: Json, direction: Json): Consequence[Json] = {
    if (answer == null || answer.result == null || answer.evidence == null || answer.resultReference == null ||
        answer.evidence.workerIdentity == null || answer.evidence.workerIdentity.isEmpty) {
      invalid("actual Human question/answer target is absent")
    } else questionBindingC(question, source).map(q => humanBinding(owner, purpose, participant, q,
      direction.mapObject(_.add("reference", Json.obj("identity" -> Json.fromString(answer.resultReference.identity),
        "revision" -> Json.fromString(answer.resultReference.revision))).add("worker", Json.fromString(answer.evidence.workerIdentity.get)))))
  }

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)

  private[admission] def handleJson(value: WorkflowHandle): Json = Json.obj("componentIdentity" -> Json.fromString(value.componentIdentity.name),
    "workflowIdentity" -> Json.fromString(value.workflowIdentity.value), "workflowRevision" -> Json.fromString(value.workflowRevision.value),
    "instanceIdentity" -> Json.fromString(value.instanceIdentity.value), "protocolVersion" -> Json.fromString(value.protocolVersion))
  private[admission] def operationJson(value: StateMachineOperationIdentity): Json =
    Json.obj("service" -> Json.fromString(value.service), "operation" -> Json.fromString(value.operation))
  private[admission] def handleTarget(input: JsonObject): Either[String, Json] =
    strings(input, Vector("componentIdentity", "workflowIdentity", "workflowRevision", "instanceIdentity", "protocolVersion")).map(Json.fromJsonObject)
  private[admission] def operationTarget(input: JsonObject): Either[String, Json] = strings(input, Vector("service", "operation")).map(Json.fromJsonObject)
  private[admission] def referenceTarget(input: JsonObject): Either[String, Json] = strings(input, Vector("identity", "revision")).map(Json.fromJsonObject)
  private def snapshotTarget(input: JsonObject): Either[String, Json] = strings(input, Vector("head", "tree")).map(Json.fromJsonObject)
  private def obj(value: Json): Either[String, JsonObject] = Option(value).flatMap(_.asObject).toRight("Human attestation must be an object")
  private def at(input: JsonObject, key: String): Either[String, JsonObject] = input(key).toRight("Human requires " + key).flatMap(obj)
  private def optionalObject(input: JsonObject, key: String): Either[String, Option[JsonObject]] = input(key).filterNot(_.isNull) match {
    case None => Right(None)
    case Some(value) => obj(value).map(Some(_))
  }
  private def text(input: JsonObject, key: String): Either[String, String] = input(key).flatMap(_.asString).toRight("Human requires String " + key)
  private def optionalText(input: JsonObject, key: String): Either[String, Option[String]] = input(key).filterNot(_.isNull) match {
    case None => Right(None)
    case Some(_) => text(input, key).map(Some(_))
  }
  private def strings(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty))((prior, key) => for {
      fields <- prior
      value <- text(input, key)
    } yield fields.add(key, Json.fromString(value)))
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Human host attestation " + reason)
}
