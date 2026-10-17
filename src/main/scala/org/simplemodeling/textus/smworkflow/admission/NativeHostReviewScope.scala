package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import NativeHostEvidence.Purpose

/** Selected independent review targets; native completion owns result validity.
  * @since   Oct.  8, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeHostReviewScope {
  def supports(purpose: Purpose): Boolean = Set(Purpose.GoalReview, Purpose.RepositoryReview)(purpose)
  def select(purpose: Purpose, binding: Json): Either[String, Json] =
    if (!supports(purpose)) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      body <- at(root, "attestation")
      input <- at(body, "target")
      target <- if (purpose == Purpose.GoalReview) goal(input) else repository(input)
      result <- at(body, "result").flatMap(resultTarget(_, purpose))
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "attestation" -> Json.obj(
      "kind" -> Json.fromString(if (purpose == Purpose.GoalReview) "goal-review" else "repository-review"),
      "target" -> target, "result" -> result))

  private def goal(input: JsonObject): Either[String, Json] = for {
    fields <- strings(input, Vector("instance", "sourceRevision", "workKind", "checksReceipt"))
    step <- optionalText(input, "step")
    generation <- input("generation").flatMap(_.asNumber).flatMap(_.toLong).toRight("Review generation must be an integer")
    plan <- at(input, "plan").flatMap(strings(_, Vector("identity", "revision", "phase", "repository")))
    base <- at(input, "base").flatMap(snapshot)
    candidate <- at(input, "candidate").flatMap(snapshot)
    paths <- stringArray(input, "changedPaths")
    checks <- input("testChecks") match {
      case None => Right(None)
      case Some(value) => checkArray(value).map(Some(_))
    }
    acceptance <- _effective(input)
    selected = fields.add("step", step.map(Json.fromString).getOrElse(Json.Null))
    .add("generation", Json.fromLong(generation)).add("plan", Json.fromJsonObject(plan)).add("base", base)
    .add("candidate", candidate).add("changedPaths", Json.fromValues(paths.map(Json.fromString)))
    checked = checks.fold(selected)(value => selected.add("testChecks", value))
  } yield Json.fromJsonObject(acceptance.fold(checked)(value => checked.add("effectiveAcceptance", value)))

  private def repository(input: JsonObject): Either[String, Json] = for {
    fields <- strings(input, Vector("instance", "validatedRevision", "localTip", "remoteTip", "indexTree", "validationMode"))
    paths <- stringArray(input, "changedPaths")
    selected <- stringArray(input, "selectedChecks")
    facts <- input("checks").toRight("Review requires checks").flatMap(checkArray)
  } yield Json.fromJsonObject(fields.add("changedPaths", Json.fromValues(paths.map(Json.fromString)))
    .add("selectedChecks", Json.fromValues(selected.map(Json.fromString))).add("checks", facts))

  private def checkArray(value: Json): Either[String, Json] =
    value.asArray.toRight("Review checks must be an array").flatMap(_.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty))(
      (prior, value) => for { values <- prior; receipt <- check(value) } yield values :+ receipt)).map(Json.fromValues)

  private def check(value: Json): Either[String, Json] = for {
    receipt <- obj(value)
    fields <- strings(receipt, Vector("identity", "termination"))
    exit <- receipt("exitCode").flatMap(_.asNumber).flatMap(_.toInt).toRight("Review exitCode must be an integer")
    sbt <- receipt("sbt") match {
      case None => Right(None)
      case Some(value) => value.as[WorkflowSbtValidation.Facts].left.map(_.message).map(facts => Some(facts.asJson))
    }
    selected = fields.add("exitCode", Json.fromInt(exit))
  } yield Json.fromJsonObject(sbt.fold(selected)(facts => selected.add("sbt", facts)))

  private def resultTarget(input: JsonObject, purpose: Purpose): Either[String, Json] = for {
    fields <- strings(input, Vector("runId", "continuationId", "typeIdentity", "worker"))
    handle <- at(input, "handle").flatMap(NativeHostHumanScope.handleTarget)
    reference <- at(input, "reference").flatMap(NativeHostHumanScope.referenceTarget)
    body <- at(input, "verdict")
    verdict <- if (purpose == Purpose.GoalReview) goalVerdict(body) else repositoryVerdict(body)
  } yield Json.fromJsonObject(fields.add("handle", handle).add("reference", reference).add("verdict", verdict))

  private def goalVerdict(input: JsonObject): Either[String, Json] = for {
    outcome <- text(input, "outcome")
    findings <- stringArray(input, "findings")
    summary <- if (outcome == "repair") text(input, "repairSummary").map(Json.fromString) else Right(Json.Null)
    acceptance <- _report(input)
    selected = Json.obj("outcome" -> Json.fromString(outcome), "findings" -> Json.fromValues(findings.map(Json.fromString)), "repairSummary" -> summary)
  } yield acceptance.fold(selected)(value => selected.mapObject(_.add("acceptance", value)))

  private def _effective(input: JsonObject): Either[String, Option[Json]] = input("effectiveAcceptance").filterNot(_.isNull) match {
    case None => Right(None)
    case Some(value) => for {
      parsed <- value.as[GoalPhaseAcceptanceBoundary.Effective].left.map(_.message)
      _ <- Either.cond(GoalPhaseAcceptanceBoundary.validateEffectiveC(parsed).isSuccess, (), "Review effective acceptance is invalid")
    } yield Some(parsed.asJson)
  }
  private def _report(input: JsonObject): Either[String, Option[Json]] = input("acceptance").filterNot(_.isNull) match {
    case None => Right(None)
    case Some(value) => for {
      parsed <- value.as[GoalPhaseAcceptanceBoundary.Report].left.map(_.message)
      _ <- Either.cond(GoalPhaseAcceptanceBoundary.validateReportC(parsed).isSuccess, (), "Review acceptance report is invalid")
    } yield Some(parsed.asJson)
  }

  private def repositoryVerdict(input: JsonObject): Either[String, Json] = for {
    decision <- text(input, "decision")
    rows <- input("findings").flatMap(_.asArray).toRight("Review findings must be an array")
    findings <- rows.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty))((prior, value) => for {
      collected <- prior
      finding <- obj(value)
      identity <- text(finding, "identity")
      paths <- stringArray(finding, "affectedPaths")
      rationale <- if (decision == "repair") text(finding, "repairRationale").map(Json.fromString) else Right(Json.Null)
    } yield collected :+ Json.obj("identity" -> Json.fromString(identity), "affectedPaths" -> Json.fromValues(paths.map(Json.fromString)), "repairRationale" -> rationale))
  } yield Json.obj("decision" -> Json.fromString(decision), "findings" -> Json.fromValues(findings))

  private def snapshot(input: JsonObject): Either[String, Json] = strings(input, Vector("head", "tree")).map(Json.fromJsonObject)
  private def obj(value: Json): Either[String, JsonObject] = Option(value).flatMap(_.asObject).toRight("Review attestation must be an object")
  private def at(input: JsonObject, key: String): Either[String, JsonObject] = input(key).toRight("Review requires " + key).flatMap(obj)
  private def text(input: JsonObject, key: String): Either[String, String] = input(key).flatMap(_.asString).toRight("Review requires String " + key)
  private def optionalText(input: JsonObject, key: String): Either[String, Option[String]] = input(key).filterNot(_.isNull) match {
    case None => Right(None)
    case Some(_) => text(input, key).map(Some(_))
  }
  private def stringArray(input: JsonObject, key: String): Either[String, Vector[String]] =
    input(key).flatMap(_.asArray).toRight("Review requires array " + key).flatMap(_.foldLeft[Either[String, Vector[String]]](Right(Vector.empty))(
      (prior, value) => for { values <- prior; text <- value.asString.toRight("Review array requires Strings") } yield values :+ text))
  private def strings(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty))((prior, key) => for {
      fields <- prior
      value <- text(input, key)
    } yield fields.add(key, Json.fromString(value)))
}
