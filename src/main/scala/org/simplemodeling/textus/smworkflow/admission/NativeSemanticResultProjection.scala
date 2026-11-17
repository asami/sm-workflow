package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.protocol.{Property, Request}
import org.simplemodeling.textus.smworkflow.value as V

/** Finite public worker artifact projection; neither submits nor grants evidence authority. */
private[admission] object NativeSemanticResultProjection {
  private val schema = "sm-workflow.public-worker-return.v1"
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Semantic result projection: " + message)
  private def jsonC(text: String): Consequence[Json] = parse(text).fold(e => invalid(e.message), Consequence.success)
  private def field(value: Json, camel: String, snake: String): Option[Json] = {
    val obj = value.asObject
    if (obj.exists(o => o.contains(camel) && o.contains(snake))) None
    else obj.flatMap(o => o(camel).orElse(o(snake)))
  }
  private def string(value: Json, camel: String, snake: String): Option[String] =
    field(value, camel, snake).flatMap(_.asString).filter(_.trim.nonEmpty)

  def projectC(workResource: String, resultResource: String, format: Option[String],
    readWork: String => Consequence[String], readResult: String => Consequence[String]): Consequence[Json] = for {
    _ <- if (Option(workResource).exists(_.startsWith(WorkflowRetainedWork.resourcePrefix)) &&
      Option(resultResource).exists(_.startsWith("file:")) && format.forall(Set("summary", "public-result")))
      Consequence.unit else invalid("requires retained work, file result and a supported format")
    workText <- readWork(workResource)
    work <- jsonC(workText)
    detailText <- readResult(resultResource)
    checkedDetail <- GoalPhasePrerequisitePublicationRecovery.validateBusinessTextC(detailText)
    detail <- jsonC(checkedDetail)
    context <- field(work, "issuedWorkContext", "issued_work_context").filter(_.isObject)
      .map(Consequence.success).getOrElse(invalid("issued context is absent or ambiguous"))
    resultType <- string(work, "resultType", "result_type").filter(Set("GoalPhaseWorkResult", "GoalTaskWorkResult"))
      .map(Consequence.success).getOrElse(invalid("unsupported issued result type"))
    _ <- if (work.hcursor.get[String]("kind").toOption.contains("Request") &&
      string(context, "resultType", "result_type").contains(resultType)) Consequence.unit
      else invalid("issued type or context differs")
    envelopeKeys = Set("schemaVersion", "schema_version", "payload", "resultType", "result_type",
      "issuedWorkContext", "issued_work_context", "workResource", "work_resource", "result")
    result <- if (detail.asObject.exists(o => o.keys.exists(envelopeKeys))) {
      if (detail.asObject.exists(_.keys.toSet == Set("schemaVersion", "resultType", "workResource", "result")) &&
        detail.hcursor.get[String]("schemaVersion").toOption.contains(schema) &&
        detail.hcursor.get[String]("resultType").toOption.contains(resultType) &&
        detail.hcursor.get[String]("workResource").toOption.contains(workResource))
        detail.hcursor.downField("result").focus.filter(_.isObject).map(Consequence.success)
          .getOrElse(invalid("complete public result is absent"))
      else invalid("worker format, type or work Resource differs")
    } else if (detail.isObject) Consequence.success(detail) else invalid("complete public result is absent")
    _ <- if (result.asObject.exists(o => !o.contains("dependencyAdmission") && !o.contains("dependency_admission")))
      Consequence.unit else invalid("private admission is unsupported")
    request = Request.ofOperation("semanticResultProjection").copy(properties = List(Property("result", result.noSpaces, None)))
    _ <- if (resultType == "GoalPhaseWorkResult") NativeWorkflowResultInput.goalResultC(request).map(_ => ())
      else for {
        value <- NativeWorkflowOperationInput.valueC[V.GoalTaskWorkResult](request, "result")
        _ <- GoalPhaseFixAssessment.internalReportC(value.fixAssessment)
        _ <- IncrementalSbtDiagnosisRepair.fromPublicC(value.diagnosticRepair)
      } yield ()
    status <- result.hcursor.get[String]("outcome").toOption.filter(_.trim.nonEmpty)
      .map(Consequence.success).getOrElse(invalid("status is absent"))
    summary <- result.hcursor.get[String]("summary").toOption.filter(_.trim.nonEmpty)
      .map(Consequence.success).getOrElse(invalid("summary is absent"))
    next <- string(work, "submitOperation", "submit_operation").map(Consequence.success)
      .getOrElse(invalid("next operation is absent"))
    session <- string(work, "submitSessionId", "submit_session_id").map(Consequence.success)
      .getOrElse(invalid("submit session is absent"))
    base = Vector("status" -> Json.fromString(status), "summary" -> Json.fromString(summary),
      "nextOperation" -> Json.fromString(next), "submitSessionId" -> Json.fromString(session),
      "resultType" -> Json.fromString(resultType), "workResource" -> Json.fromString(workResource),
      "resultResource" -> Json.fromString(resultResource))
  } yield Json.obj((base ++ Option.when(format.contains("public-result"))("resultJson" -> Json.fromString(result.noSpaces))): _*)
}
