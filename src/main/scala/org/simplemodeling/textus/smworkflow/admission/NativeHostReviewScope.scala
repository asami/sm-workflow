package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.cncf.workflow.WorkflowProtocolV1
import NativeHostEvidence.Purpose

/** Independent attestation of the actual assigned candidate and disposition,
  * not unchanged diagnostic Context, full Plan or complete result transport.
  * A review attestation never supplies a process/effect grant.
  * @since   Oct.  8, 2026
  * @version Oct.  9, 2026
  */
private[smworkflow] object NativeHostReviewScope {
  def supports(purpose: Purpose): Boolean = Set(Purpose.GoalReview, Purpose.RepositoryReview)(purpose)

  def select(purpose: Purpose, binding: Json, legacy: Boolean): Either[String, Json] =
    if (!supports(purpose)) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      current = root("attestation").nonEmpty
      _ <- Either.cond(current || legacy, (), "Independent review requires actual attestation, not a copied body")
      _ <- shape(root, Set("componentInstance", if (current) "attestation" else "value"), true)
      fields <- at(root, if (current) "attestation" else "value")
      _ <- shape(fields, Set("kind", "target", "result"), current)
      kind = if (purpose == Purpose.GoalReview) "goal-review" else "repository-review"
      _ <- Either.cond(!current || fields("kind").flatMap(_.asString).contains(kind), (), "Independent review purpose is incompatible")
      input <- if (current) at(fields, "target") else for {
        envelope <- at(fields, "input")
        version <- text(envelope, "schemaVersion")
        _ <- Either.cond(if (purpose == Purpose.GoalReview) Set("sm-workflow.goal-phase-native-work-request.v1", "sm-workflow.goal-phase-native-work-request.v2")(version)
          else Set("sm-workflow.repository-native-merge-review-request.v1", "sm-workflow.repository-native-merge-review-request.v2")(version),
          (), "Independent review input format is unsupported")
        payload <- at(envelope, "payload")
      } yield payload
      target <- if (purpose == Purpose.GoalReview) goal(input, current) else repository(input, current)
      rawResult <- at(fields, "result")
      result <- resultTarget(rawResult, purpose, current)
      handle <- at(result.asObject.get, "handle")
      _ <- Either.cond(handle("instanceIdentity") == target.asObject.get.apply("instance"), (), "Independent review crosses its candidate instance")
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "attestation" -> Json.obj(
      "kind" -> Json.fromString(kind), "target" -> target, "result" -> result))

  private def goal(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("instance", "sourceRevision", "workKind", "step", "generation", "plan", "base", "candidate", "checksReceipt", "changedPaths"), current)
    instance <- text(input, if (current) "instance" else "instanceIdentity")
    source <- text(input, "sourceRevision")
    kind <- text(input, if (current) "workKind" else "kind")
    _ <- Either.cond(Set("ReviewStep", "ReviewPhase")(kind), (), "Independent Goal review has no actual review assignment")
    step <- optionalText(input, if (current) "step" else "stepIdentity")
    _ <- Either.cond((kind == "ReviewStep") == step.nonEmpty, (), "Independent review has another Step/Phase target")
    generation <- input("generation").flatMap(_.asNumber).flatMap(_.toLong).filter(_ >= 0).toRight("Review generation is invalid")
    plan <- at(input, if (current) "plan" else "selectedPlan")
    _ <- shape(plan, Set("identity", "revision", "phase", "repository"), current)
    planFields <- strings(plan, Vector("identity", "revision", "phase", "repository"))
    base <- snapshot(input, if (current) "base" else "originalBase", current)
    candidate <- snapshot(input, "candidate", current)
    checks <- text(input, "checksReceipt")
    changed <- if (current) paths(input, "changedPaths") else at(input, "review").flatMap(paths(_, "changedPaths"))
  } yield Json.obj("instance" -> Json.fromString(instance), "sourceRevision" -> Json.fromString(source), "workKind" -> Json.fromString(kind),
    "step" -> step.map(Json.fromString).getOrElse(Json.Null), "generation" -> Json.fromLong(generation),
    "plan" -> Json.fromJsonObject(planFields), "base" -> base, "candidate" -> candidate,
    "checksReceipt" -> Json.fromString(checks), "changedPaths" -> Json.fromValues(changed.map(Json.fromString)))

  private def repository(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("instance", "validatedRevision", "localTip", "remoteTip", "indexTree", "changedPaths", "validationMode", "selectedChecks", "checks"), current)
    instance <- text(input, if (current) "instance" else "instanceIdentity")
    source <- text(input, "validatedRevision")
    tips <- oids(input, Vector("localTip", "remoteTip", "indexTree"))
    changed <- paths(input, "changedPaths")
    mode <- text(input, "validationMode")
    _ <- Either.cond(Set("required", "explicit-no-product-tests")(mode), (), "Review validation mode is incompatible")
    selected <- stringArray(input, "selectedChecks")
    checks <- input("checks").flatMap(_.asArray).toRight("Review selected check facts are absent")
    facts <- checks.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, value) => for {
      values <- prior
      receipt <- obj(value)
      _ <- shape(receipt, Set("identity", "termination", "exitCode"), current)
      identity <- text(receipt, "identity")
      termination <- text(receipt, "termination")
      exit <- receipt("exitCode").flatMap(_.asNumber).flatMap(_.toInt).toRight("Review check outcome is absent")
      _ <- Either.cond(termination == "exited", (), "Review check has no actual exit")
    } yield values :+ Json.obj("identity" -> Json.fromString(identity), "termination" -> Json.fromString(termination), "exitCode" -> Json.fromInt(exit)) }
    _ <- Either.cond(facts.map(_.hcursor.get[String]("identity").toOption.get) == Vector("index-local", "index-remote") ++ selected,
      (), "Review check coverage differs from the actual selection")
  } yield Json.fromJsonObject(tips.add("instance", Json.fromString(instance)).add("validatedRevision", Json.fromString(source))
    .add("changedPaths", Json.fromValues(changed.map(Json.fromString))).add("validationMode", Json.fromString(mode))
    .add("selectedChecks", Json.fromValues(selected.map(Json.fromString))).add("checks", Json.fromValues(facts)))

  private def resultTarget(input: JsonObject, purpose: Purpose, current: Boolean): Either[String, Json] = for {
    // Earlier attestations copied these control fields. Their owning completion
    // already resolves the issued context and checks the live state/CAS. Read
    // and discard the old copies rather than requiring an unchanged witness.
    _ <- shape(input.remove("expectedRevision").remove("modelRevision"),
      Set("handle", "runId", "continuationId", "reference", "typeIdentity", "worker", "verdict"), current)
    _ <- if (current) Right(()) else Either.cond(input("schemaVersion").flatMap(_.asString).contains(WorkflowProtocolV1.schemaVersion) &&
      input("kind").flatMap(_.asString).contains("CONTINUATION_RESULT"), (), "Review result transport is unsupported")
    handle <- at(input, "handle")
    _ <- shape(handle, Set("componentIdentity", "workflowIdentity", "workflowRevision", "instanceIdentity", "protocolVersion"), true)
    handleFields <- strings(handle, Vector("componentIdentity", "workflowIdentity", "workflowRevision", "instanceIdentity", "protocolVersion"))
    _ <- Either.cond(handleFields("protocolVersion").flatMap(_.asString).contains(WorkflowProtocolV1.schemaVersion), (), "Review protocol is incompatible")
    fields <- strings(input, Vector("runId", "continuationId"))
    payload <- if (current) Right(input) else at(input, "result")
    ref <- at(payload, "reference")
    _ <- shape(ref, Set("identity", "revision"), true)
    refFields <- strings(ref, Vector("identity", "revision"))
    resultType <- text(payload, "typeIdentity")
    _ <- Either.cond(resultType == (if (purpose == Purpose.GoalReview) GoalPhaseNativeWork.resultCodec.typeIdentity
      else RepositorySyncNativeMergeReview.resultCodec.typeIdentity), (), "Review result has another actual type")
    worker <- if (current) text(input, "worker") else at(input, "executionEvidence").flatMap(text(_, "workerIdentity"))
    value <- if (current) at(input, "verdict") else for {
      envelope <- at(payload, "value")
      schema <- text(envelope, "schemaVersion")
      _ <- Either.cond(schema == (if (purpose == Purpose.GoalReview) "sm-workflow.goal-phase-native-work-result.v1"
        else "sm-workflow.repository-native-merge-review-result.v1"), (), "Review disposition format is unsupported")
      body <- at(envelope, "payload")
    } yield body
    verdict <- if (purpose == Purpose.GoalReview) goalVerdict(value, current) else repositoryVerdict(value, current)
  } yield Json.fromJsonObject(fields.add("handle", Json.fromJsonObject(handleFields))
    .add("reference", Json.fromJsonObject(refFields)).add("typeIdentity", Json.fromString(resultType)).add("worker", Json.fromString(worker)).add("verdict", verdict))

  private def goalVerdict(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("outcome", "findings", "repairSummary"), current)
    outcome <- text(input, "outcome")
    _ <- Either.cond(Set("accept", "repair", "decision-needed")(outcome), (), "Goal review disposition is unsupported")
    findings <- semanticArray(input, "findings")
    summary <- if (outcome == "repair") semanticText(input, if (current) "repairSummary" else "summary").map(Json.fromString)
      else Right(Json.Null)
    _ <- Either.cond(!current || outcome == "repair" || input("repairSummary").exists(_.isNull), (), "Accepted review cannot carry a repair directive")
    _ <- Either.cond(outcome != "accept" || findings.isEmpty, (), "Accepted review contains unresolved findings")
    _ <- Either.cond(outcome != "repair" || findings.nonEmpty, (), "Repair review has no actual findings")
  } yield Json.obj("outcome" -> Json.fromString(outcome), "findings" -> Json.fromValues(findings.map(Json.fromString)), "repairSummary" -> summary)

  private def repositoryVerdict(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("decision", "findings"), current)
    decision <- text(input, "decision")
    _ <- Either.cond(Set("accept", "repair", "reject", "decision-needed")(decision), (), "Repository review disposition is unsupported")
    values <- input("findings").flatMap(_.asArray).toRight("Repository review findings are absent")
    findings <- values.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, value) => for {
      collected <- prior
      finding <- obj(value)
      _ <- shape(finding, Set("identity", "affectedPaths", "repairRationale"), current)
      identity <- text(finding, "identity")
      affected <- paths(finding, "affectedPaths")
      rationale <- if (decision == "repair") semanticText(finding, if (current) "repairRationale" else "rationale").map(Json.fromString)
        else Right(Json.Null)
      _ <- Either.cond(!current || decision == "repair" || finding("repairRationale").exists(_.isNull), (), "Nonrepair review cannot carry a repair directive")
    } yield collected :+ Json.obj("identity" -> Json.fromString(identity), "affectedPaths" -> Json.fromValues(affected.map(Json.fromString)), "repairRationale" -> rationale) }
    _ <- Either.cond(decision != "accept" || findings.isEmpty, (), "Accepted merge review contains unresolved findings")
    _ <- Either.cond(decision != "repair" || findings.nonEmpty, (), "Repair merge review has no actual findings")
  } yield Json.obj("decision" -> Json.fromString(decision), "findings" -> Json.fromValues(findings))

  private def at(input: JsonObject, key: String): Either[String, JsonObject] = input(key).toRight("Review requires actual " + key).flatMap(obj)
  private def obj(value: Json): Either[String, JsonObject] = Option(value).flatMap(_.asObject).toRight("Review attestation must be an object")
  private def shape(input: JsonObject, keys: Set[String], strict: Boolean): Either[String, Unit] =
    Either.cond(!strict || input.keys.toSet == keys, (), "Review attestation contains undeclared or missing fields")
  private def text(input: JsonObject, key: String): Either[String, String] = input(key).flatMap(_.asString)
    .filter(v => v.nonEmpty && v.trim == v && !v.exists(_.isControl)).toRight("Review requires actual " + key)
  private def optionalText(input: JsonObject, key: String): Either[String, Option[String]] = input(key).filterNot(_.isNull) match {
    case None => Right(None)
    case Some(_) => text(input, key).map(Some(_))
  }
  private def semanticText(input: JsonObject, key: String): Either[String, String] = input(key).flatMap(_.asString)
    .filter(v => v.trim.nonEmpty && !v.contains('\u0000') && v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384)
    .toRight("Review semantic directive is absent or invalid")
  private def strings(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty)) { (prior, key) => for {
      fields <- prior
      value <- text(input, key)
    } yield fields.add(key, Json.fromString(value)) }
  private def stringArray(input: JsonObject, key: String): Either[String, Vector[String]] = input(key).flatMap(_.asArray)
    .toRight("Review selected " + key + " is absent").flatMap(values =>
      values.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) { (prior, value) => for {
        items <- prior
        item <- value.asString.filter(v => v.nonEmpty && v.trim == v && !v.exists(_.isControl)).toRight("Review selected fact is invalid")
      } yield items :+ item })
  private def semanticArray(input: JsonObject, key: String): Either[String, Vector[String]] = input(key).flatMap(_.asArray)
    .filter(_.size <= 128).toRight("Review findings are absent or oversized").flatMap(values =>
      values.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) { (prior, value) => for {
        items <- prior
        item <- semanticText(JsonObject("item" -> value), "item")
      } yield items :+ item })
  private def paths(input: JsonObject, key: String): Either[String, Vector[String]] = stringArray(input, key)
    .flatMap(values => Either.cond(values.distinct == values && values.forall(RepositorySyncNativeConflictInput.relative), values, "Review coverage paths are invalid"))
  private def oids(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] = strings(input, keys)
    .flatMap(fields => Either.cond(keys.forall(k => fields(k).flatMap(_.asString).exists(_.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))),
      fields, "Review native target is invalid"))
  private def snapshot(input: JsonObject, key: String, strict: Boolean): Either[String, Json] = for {
    value <- at(input, key)
    _ <- shape(value, Set("head", "tree"), strict)
    fields <- oids(value, Vector("head", "tree"))
  } yield Json.fromJsonObject(fields)
}
