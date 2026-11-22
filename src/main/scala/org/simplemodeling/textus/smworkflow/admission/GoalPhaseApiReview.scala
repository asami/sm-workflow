/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.resource.{ResourceReadLimits, ResourceReference}
import org.goldenport.cncf.provider.{ProviderCall, ProviderEngine, ProviderRequest}
import org.goldenport.cncf.spi.ai.runner.{AiRecordRequest, AiRunnerRequirement, AiRunnerSocketSet}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}

/** Explicit one-attempt API transport. Submit and attempt retention stay with
  * their existing owners; this operation never retries or completes the work. */
private[smworkflow] object GoalPhaseApiReview {
  private val maximum = 512 * 1024
  private[admission] val reviewResultInstructions = "Return GoalPhaseWorkResult JSON with outcome, summary and findings. The outcome must be exactly \"accept\", \"repair\", or \"decision-needed\"; do not use \"accepted\". Use accept only with empty findings and no blocking unresolved issues; repair requires findings; decision-needed identifies unresolved decisions. Apply the issued acceptance and instructions; report each applicable required condition in acceptance. For every relevant REVIEW issue, issueReport.dispositions must contain issueIdentity, disposition and rationale: RESOLVED, RECURRING or NOT_REVIEWED. RECURRING requires a unique 1-based findingOrdinal; other dispositions omit it. Report relevant non-REVIEW issues when needed to resolve them. Do not claim unobserved resolution. An accepting ReviewPhase may carry an original OPEN finding wholly within the actual adopted Phase deferral. Keep its NOT_REVIEWED disposition and unmet observations, and supply issueReport.deferredIssues with {issueIdentity,conditionIdentities} for each entirely covered issue. An uncertain, partial or required-scope finding remains blocking. Only an issued ReviewStep purposeRepair contract permits original-purpose repair findings: repairFindingSources is {sources:[...]} with one 1-based findingOrdinal per finding; required-condition uses conditionIdentity; step-purpose uses stepIdentity, rationale and affectedPaths. Cover every required failure. reviewObservations uses {items:[{identity,category,summary,rationale}]}. Only a ReviewStep repair whose sole remaining findings are terminal-LF corrections may use completionConditions {kind:\"terminal-lf\",paths:[...]}, one owned path per finding. Only ReviewPhase decision-needed with findings may propose planRevision {identity,revision,reason,plan}; a review may instead request dependencyProposal for a decision, never both proposals. Omit inapplicable optional fields instead of null, and identify evidence gaps.\n"
  val maxBytes: ComponentParameterKey[Int] = ComponentParameterKey.optional(
    "sm-workflow.goal-phase.review-package-max-bytes", ComponentParameterDecoder {
      case ConfigurationValue.NumberValue(n) if n.isWhole && n.isValidInt && n.toInt > 0 && n.toInt <= maximum =>
        Consequence.success(n.toInt)
      case ConfigurationValue.StringValue(s) if s != null && s.matches("[1-9][0-9]*") &&
          scala.util.Try(s.toInt).toOption.exists(_ <= maximum) => Consequence.success(s.toInt)
      case _ => Consequence.configurationInvalid("review package limit must be 1..524288 bytes")
    })
  private[admission] def packageLimitC(core: ActionCall.Core): Consequence[Int] =
    core.component.map(_.initializationParameters.resolve(maxBytes).map(_.value.getOrElse(maximum)))
      .getOrElse(Consequence.success(maximum))

  private def exact(s: String): Boolean = s != null && s.nonEmpty && s.trim == s && !s.exists(_.isControl)
  private val string = Record.dataAuto("type" -> "string")
  private val strings = Record.dataAuto("type" -> "array", "items" -> string)
  private val observation = Record.dataAuto("type" -> "object", "required" -> Vector("identity", "status", "detail"),
    "properties" -> Record.dataAuto("identity" -> string, "status" -> string, "detail" -> string))
  private val acceptance = Record.dataAuto("type" -> "object", "required" -> Vector("conditions", "advisories"),
    "properties" -> Record.dataAuto("conditions" -> Record.dataAuto("type" -> "array", "items" -> observation),
      "advisories" -> strings))
  private val objectSchema = Record.dataAuto("type" -> "object")
  private val source = Record.dataAuto("type" -> "object", "required" -> Vector("kind", "findingOrdinal"),
    "properties" -> Record.dataAuto("kind" -> string, "findingOrdinal" -> Record.dataAuto("type" -> "integer"),
      "conditionIdentity" -> string, "stepIdentity" -> string, "rationale" -> string, "affectedPaths" -> strings))
  private val sourceWrapper = Record.dataAuto("type" -> "object", "required" -> Vector("sources"),
    "properties" -> Record.dataAuto("sources" -> Record.dataAuto("type" -> "array", "items" -> source)))
  private val reviewObservation = Record.dataAuto("type" -> "object", "required" -> Vector("identity", "category", "summary", "rationale"),
    "properties" -> Record.dataAuto("identity" -> string, "category" -> string, "summary" -> string, "rationale" -> string))
  private val reviewObservations = Record.dataAuto("type" -> "object", "required" -> Vector("items"),
    "properties" -> Record.dataAuto("items" -> Record.dataAuto("type" -> "array", "items" -> reviewObservation)))
  private val issueDisposition = Record.dataAuto("type" -> "object", "required" -> Vector("issueIdentity", "disposition", "rationale"),
    "properties" -> Record.dataAuto("issueIdentity" -> string, "disposition" -> string, "rationale" -> string,
      "findingOrdinal" -> Record.dataAuto("type" -> "integer")))
  private val issueDeferral = Record.dataAuto("type" -> "object", "required" -> Vector("issueIdentity", "conditionIdentities"),
    "properties" -> Record.dataAuto("issueIdentity" -> string, "conditionIdentities" -> strings))
  private val issueReport = Record.dataAuto("type" -> "object", "required" -> Vector("dispositions"),
    "properties" -> Record.dataAuto("dispositions" -> Record.dataAuto("type" -> "array", "items" -> issueDisposition),
      "deferredIssues" -> Record.dataAuto("type" -> "array", "items" -> issueDeferral)))
  private val completionConditions = Record.dataAuto("type" -> "object", "required" -> Vector("kind", "paths"),
    "properties" -> Record.dataAuto("kind" -> string, "paths" -> strings))
  private val reference = Record.dataAuto("type" -> "object", "required" -> Vector("identity", "revision"),
    "properties" -> Record.dataAuto("identity" -> string, "revision" -> string))
  private val component = Record.dataAuto("type" -> "object", "required" -> Vector("identity", "repository", "workspace"),
    "properties" -> Record.dataAuto("identity" -> string, "repository" -> string, "workspace" -> string))
  private val boundary = Record.dataAuto("type" -> "object", "required" -> Vector("schemaVersion", "purpose", "permittedOperations", "excludedPaths"),
    "properties" -> Record.dataAuto("schemaVersion" -> string, "purpose" -> reference, "permittedOperations" -> strings,
      "excludedPaths" -> strings, "exactPaths" -> strings))
  private val scopeUpdate = Record.dataAuto("type" -> "object", "required" -> Vector("approval", "rationale"),
    "properties" -> Record.dataAuto("approval" -> reference, "rationale" -> string))
  private val dependencyProposal = Record.dataAuto("type" -> "object", "required" -> Vector("identity", "revision", "discovery",
    "requester", "dependency", "usageContract", "reason", "paths", "acceptance", "dependentWork", "discoveredEdits"),
    "properties" -> Record.dataAuto("identity" -> string, "revision" -> string, "discovery" -> string,
      "requester" -> component, "dependency" -> component, "usageContract" -> reference, "reason" -> string,
      "paths" -> strings, "acceptance" -> strings, "dependentWork" -> Record.dataAuto("type" -> "array", "items" -> reference),
      "discoveredEdits" -> strings, "authorizationBoundary" -> boundary, "scopeUpdate" -> scopeUpdate))
  private val planRevision = Record.dataAuto("type" -> "object", "required" -> Vector("identity", "revision", "reason", "plan"),
    "properties" -> Record.dataAuto("identity" -> string, "revision" -> string, "reason" -> string, "plan" -> objectSchema))
  private[admission] val schema = Record.dataAuto("type" -> "object", "required" -> Vector("outcome", "summary", "findings"),
    "properties" -> Record.dataAuto("outcome" -> Record.dataAuto("type" -> "string", "enum" -> Vector("accept", "repair", "decision-needed")), "summary" -> string,
      "findings" -> strings, "acceptance" -> acceptance, "repairFindingSources" -> sourceWrapper,
      "reviewObservations" -> reviewObservations, "issueReport" -> issueReport, "dependencyProposal" -> dependencyProposal,
      "completionConditions" -> completionConditions, "planRevision" -> planRevision))

  private def normalized(s: String): Consequence[Json] =
    io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(s).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(Consequence.stateConflict(_), Consequence.success)

  private def field(value: Json, name: String): Json = value.hcursor.downField(name).focus.getOrElse(Json.Null)
  private[admission] def checkFacts(value: Json): Option[Json] = value.asArray.filter(_.nonEmpty).flatMap { checks =>
    if (!checks.forall(c => c.hcursor.get[String]("identity").toOption.exists(_.nonEmpty))) None
    else Some(Json.fromValues(checks.map(c => Json.obj(Vector("identity", "termination", "exitCode", "sbt")
      .map(k => k -> field(c, k))*))))
  }
  private[admission] def compactIssues(value: Json): Option[Json] = {
    if (value.isNull) Some(Json.Null)
    else value.hcursor.downField("issues").focus.flatMap(_.asArray).flatMap { issues =>
      val projected = issues.map { issue =>
        val summary = issue.hcursor.get[String]("summary").toOption
        if (summary.exists(s => s.codePointCount(0, s.length) > 1024)) {
          if (!issue.hcursor.get[String]("evidenceReference").toOption.exists(_.nonEmpty)) None
          else issue.asObject.map(o => Json.fromJsonObject(o.add("summary", Json.fromString("[Diagnostic retained at evidenceReference]"))))
        } else Some(issue)
      }
      if (projected.exists(_.isEmpty)) None
      else value.asObject.map(o => Json.fromJsonObject(o.add("issues", Json.fromValues(projected.flatten))))
    }
  }
  private def contract(work: Json, issued: Json): Option[Json] = {
    val plan = field(work, "selectedPlan")
    val context = field(issued, "context")
    compactIssues(field(work, "issues")).map(issues => Json.obj(
      "selectedPlan" -> field(work, "selectedPlan"), "stepIdentity" -> field(work, "stepIdentity"), "effectiveAcceptance" -> field(plan, "effectiveAcceptance"),
      "acceptance" -> field(plan, "acceptance"), "purposeRepair" -> field(work, "purposeRepair"),
      "issues" -> issues, "repairDirective" -> field(work, "repairDirective"),
      "releaseRepairContract" -> field(work, "releaseRepairContract"),
      "instructions" -> field(context, "summary"), "requiredFacts" -> field(context, "requiredFacts"),
      "completion" -> field(issued, "completion"), "evidence" -> field(issued, "evidence")))
  }

  private def reviewContext(review: Json): Json = Json.obj(
    (Vector("metadata", "priorReviews", "dependencyValidations").map { key =>
      key -> review.hcursor.downField(key).focus.getOrElse(Json.arr())
    } ++ Vector("triageJudgment", "qualification").map(key => key -> field(review, key)))*)

  private[admission] def packageC(packageJson: String, actual: V.GoalPhaseAdvanceResult,
    workResource: String, limit: Int): Consequence[Boolean] = {
    if (packageJson == null || packageJson.getBytes(UTF_8).length > limit) Consequence.success(false)
    else normalized(packageJson) match {
      case Consequence.Failure(_) => Consequence.success(false)
      case Consequence.Success(packet) => for {
      work <- actual.work.map(_.toRecord().toJsonStringC).getOrElse(Consequence.stateConflict("review work absent"))
      issued <- normalized(work)
      context <- actual.issuedWorkContext.map(_.toRecord().toJsonStringC)
        .getOrElse(Consequence.stateConflict("review context absent"))
      ctx <- normalized(context)
    } yield {
      val p = packet.hcursor
      val w = issued.hcursor
      val review = p.downField("review")
      val actualReview = field(issued, "review")
      val disposition = ctx.hcursor.downField("executionRequirement").downField("disposition")
      val files = p.downField("files").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      val names = files.flatMap(_.hcursor.get[String]("name").toOption)
      packet.asObject.exists(_.keys.toSet == Set("schema", "continuationId", "resultType", "kind", "purpose",
        "candidate", "review", "files", "completenessSource", "contract", "checksReceipt", "reviewContext", "workResource")) &&
        review.focus.flatMap(_.asObject).exists(_.keys.toSet == Set("diff", "checks", "changedPaths")) &&
        p.get[String]("schema").toOption.contains("sm-workflow.supplied-review.v1") &&
        p.get[String]("workResource").toOption.contains(workResource) &&
        p.get[String]("continuationId").toOption == actual.issuedWorkContext.map(_.continuationId) &&
        p.get[String]("resultType").toOption.contains("GoalPhaseWorkResult") &&
        p.downField("kind").focus == w.downField("kind").focus &&
        p.downField("purpose").focus == w.downField("purpose").focus &&
        p.downField("candidate").focus == w.downField("candidate").focus &&
        Vector("diff", "changedPaths").forall(k => field(field(packet, "review"), k) == field(actualReview, k)) &&
        checkFacts(field(actualReview, "checks")).contains(field(field(packet, "review"), "checks")) &&
        contract(issued, ctx).contains(field(packet, "contract")) &&
        field(packet, "reviewContext") == reviewContext(actualReview) &&
        w.get[String]("checksReceipt").toOption.exists(_.nonEmpty) &&
        p.downField("checksReceipt").focus == w.downField("checksReceipt").focus &&
        disposition.get[String]("placement").toOption.contains("DELEGATED") &&
        disposition.get[String]("independence").toOption.contains("REQUIRED") &&
        p.get[String]("completenessSource").toOption.exists(_.trim.nonEmpty) && files.nonEmpty &&
        names.size == files.size && names.distinct.size == names.size && names.forall(_.trim.nonEmpty) &&
        files.forall(f => f.asObject.exists(_.keys.toSet == Set("name", "content")) && f.hcursor.get[String]("content").isRight)
      }
    }
  }

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] = Vector(new Operation(port))
  private final class Operation(port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(SmWorkflowComponent.GoalPhaseServiceService.RunGoalPhaseApiReviewOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") || request.operation != "runGoalPhaseApiReview")
        Consequence.stateConflict("API review requires its exact owning operation")
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseApiReviewInput](request, "input").flatMap { input =>
        if (input != null && Vector(input.sessionId, input.workResource, input.attemptIdentity).forall(exact) && exact(input.packageResource))
          Consequence.success(Action(request, input, port))
        else Consequence.stateConflict("API review input is incomplete")
      }
  }
  private final case class Action(request: Request, input: V.GoalPhaseApiReviewInput,
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
        selected = core.copy(executionContext = context)
        actual <- port.apiReviewWorkC(input, selected)
        continuation <- actual.issuedWorkContext.map(_.continuationId).map(Consequence.success)
          .getOrElse(Consequence.stateConflict("review continuation absent"))
        limit <- packageLimitC(selected)
        reference <- NativeWorkflowResourceInput.referenceC(input.packageResource).flatMap { ref =>
          if (ref.scheme == "file") Consequence.success(ref)
          else Consequence.stateConflict("API review package requires a local file URI")
        }
        result <- acquireAndReviewC(selected.executionContext, reference, limit, actual, input, continuation,
          text => runC(selected, input, continuation, text))
      } yield NativeWorkflowOperationOutput.response(result)
    }
  }
  private[admission] def acquireAndReviewC(context: ExecutionContext, reference: ResourceReference, limit: Int,
    actual: V.GoalPhaseAdvanceResult, input: V.GoalPhaseApiReviewInput, continuation: String,
    run: String => Consequence[V.GoalPhaseApiReviewResult]): Consequence[V.GoalPhaseApiReviewResult] = {
    def declined = Consequence.success(V.GoalPhaseApiReviewResult("DECLINED", input.attemptIdentity,
      continuation, None, None, None, None, "Incomplete, oversized or crossed supplied package", None))
    context.readResourceText(reference, ResourceReadLimits(limit), Some(UTF_8)) match {
      case Consequence.Failure(conclusion) if conclusion.observation.taxonomy == Taxonomy.resourceInvalid &&
          conclusion.observation.getMessage.contains("resource exceeds the runtime byte limit") => declined
      case Consequence.Failure(conclusion) => Consequence.Failure(conclusion)
      case Consequence.Success(text) => packageC(text, actual, input.workResource, limit).flatMap { eligible =>
        if (eligible) run(text) else declined
      }
    }
  }
  // Only the runner's typed budget refusals are attributable policy blocks.
  private[admission] def blockedReason(conclusion: Conclusion): Option[String] = {
    val observation = conclusion.observation
    val operations = observation.cause.descriptor.facets.collect {
      case Descriptor.Facet.Operation(name) => name
    }
    if (observation.taxonomy == Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Illegal) &&
        operations.size == 1 && Set("ai.input-budget", "ai.cost-budget")(operations.head))
      Some(operations.head)
    else None
  }


  private[admission] def providerResult(attempt: String, continuation: String, record: Record,
    model: Option[String], responseMetadata: Map[String, String]): V.GoalPhaseApiReviewResult = {
    def safe(text: String): Boolean = text != null && text.nonEmpty && text.length <= 256 && !text.exists(_.isControl)
    val facts = responseMetadata.filter { case (key, text) =>
      (key.startsWith("ai.usage.") || Set("ai.execution.provider", "ai.execution.profile",
        "openai.response_id", "openai.finish_reason", "gemma.response_id")(key)) && safe(text)
    }
    val metadata = Option.when(facts.nonEmpty)(GoalPhaseNativeMetadata.record(
      Json.obj(facts.toVector.sortBy(_._1).map((k, v) => k -> Json.fromString(v))*)))
    val converted = for {
      _ <- GoalPhasePurposeRepair.validatePublicResultC(record)
      _ <- IncrementalSbtDiagnosisRepair.validatePublicResultC(record)
      _ <- GoalPhaseDependencyValidation.validatePublicResultC(record)
      value <- summon[ValueReader[V.GoalPhaseWorkResult]].readC(record)
    } yield value
    val value = converted.toOption
    val valid = value.exists(v => Set("accept", "repair", "decision-needed")(v.outcome))
    V.GoalPhaseApiReviewResult(if (valid) "COMPLETED" else "FAILED", attempt, continuation,
      value, model.filter(safe), responseMetadata.get("ai.execution.provider").filter(safe), metadata,
      if (valid) "Submit separately for Admission" else "Provider returned an invalid typed review result or outcome",
      Option.when(value.isEmpty)(record))
  }
  private def runC(core: ActionCall.Core, input: V.GoalPhaseApiReviewInput, continuation: String, packageJson: String): Consequence[V.GoalPhaseApiReviewResult] = {
    val provider = ProviderCall.Core(ProviderRequest("goal-phase-api-review", "review",
      Map("attempt" -> input.attemptIdentity)), core.executionContext, core.component, core.correlationId)
    ProviderEngine.execute(ReviewCall(provider, input, continuation, packageJson))
  }
  private final case class ReviewCall(core: ProviderCall.Core, input: V.GoalPhaseApiReviewInput, continuation: String, packageJson: String)
      extends ProviderCall[V.GoalPhaseApiReviewResult] {
    override def calltreeResultAttributes(result: V.GoalPhaseApiReviewResult): Map[String, String] = Map.empty
    protected def build_Program: ExecUowM[V.GoalPhaseApiReviewResult] = provider_step("goal-phase-api-review", Map.empty) {
      given ExecutionContext = executionContext
      def result(status: String, message: String) = V.GoalPhaseApiReviewResult(status, input.attemptIdentity,
        continuation, None, None, None, None, message, None)
      core.component.flatMap(_.port.get[AiRunnerSocketSet]) match {
        case None => Consequence.success(result("UNAVAILABLE", "AI runner socket unavailable before execution"))
        case Some(socket) => socket.aiRunner() match {
          case Consequence.Failure(_) => Consequence.success(result("UNAVAILABLE", "AI runner unavailable before execution"))
          case Consequence.Success(runner) =>
            runner.generateRecord(AiRecordRequest(
              "Independently review only the supplied package. Do not use tools or external context. " +
                reviewResultInstructions + packageJson,
              schema, requirement = AiRunnerRequirement(purpose = Some("software-analysis"), tools = Vector.empty),
              properties = Vector(Property("ai.record.retry-limit", "0", None)))) match {
              case Consequence.Failure(conclusion) =>
                blockedReason(conclusion) match {
                  case Some(reason) => Consequence.success(result("BLOCKED", reason))
                  case None => Consequence.success(result("UNCERTAIN", "Provider call did not return a usable result"))
                }
              case Consequence.Success(response) =>
                Consequence.success(providerResult(input.attemptIdentity, continuation, response.record, response.model, response.metadata))
            }
        }
      }
    }
  }
}
