/**
 * @since   Nov. 21, 2026
 * @version Nov. 23, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.resource.{ResourceReadLimits, ResourceReference}
import org.goldenport.cncf.provider.{ProviderCall, ProviderEngine, ProviderRequest}
import org.goldenport.cncf.spi.ComponentSelector
import org.goldenport.cncf.spi.ai.runner.{AiRecordRequest, AiRecordResponse, AiRunner, AiRunnerRequirement, AiRunnerSocketSet}
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
  private[admission] val reviewResultInstructions = "Return GoalPhaseWorkResult JSON with outcome, summary and findings. Write newly authored review summaries, findings and rationales in English. Preserve quoted approvals and log excerpts verbatim in their original language; preserve typed values and evidence references exactly. The outcome must be exactly \"accept\", \"repair\", or \"decision-needed\"; do not use \"accepted\". Use accept only with empty findings and no blocking unresolved issues; repair requires findings; decision-needed identifies unresolved decisions. Apply the issued acceptance and instructions; report each applicable required condition in acceptance. For every relevant REVIEW issue, issueReport.dispositions must contain issueIdentity, disposition and rationale: RESOLVED, RECURRING or NOT_REVIEWED. RECURRING requires a unique 1-based findingOrdinal; other dispositions omit it. Report relevant non-REVIEW issues when needed to resolve them. Do not claim unobserved resolution. An accepting ReviewPhase may carry an original OPEN finding wholly within the actual adopted Phase deferral. Keep its NOT_REVIEWED disposition and unmet observations, and supply issueReport.deferredIssues with {issueIdentity,conditionIdentities} for each entirely covered issue. An uncertain, partial or required-scope finding remains blocking. Only an issued ReviewStep purposeRepair contract permits original-purpose repair findings: repairFindingSources is {sources:[...]} with one 1-based findingOrdinal per finding; required-condition uses conditionIdentity; step-purpose uses stepIdentity, rationale and affectedPaths. Cover every required failure. reviewObservations uses {items:[{identity,category,summary,rationale}]}. Only a ReviewStep repair whose sole remaining findings are terminal-LF corrections may use completionConditions {kind:\"terminal-lf\",paths:[...]}, one owned path per finding. Only ReviewPhase decision-needed with findings may propose planRevision {identity,revision,reason,plan}; a review may instead request dependencyProposal for a decision, never both proposals. Omit inapplicable optional fields instead of null, and identify evidence gaps.\n"
  val maxBytes: ComponentParameterKey[Int] = ComponentParameterKey.optional(
    "sm-workflow.goal-phase.review-package-max-bytes", ComponentParameterDecoder {
      case ConfigurationValue.NumberValue(n) if n.isWhole && n.isValidInt && n.toInt > 0 && n.toInt <= maximum =>
        Consequence.success(n.toInt)
      case ConfigurationValue.StringValue(s) if Option(s).isDefined && s.matches("[1-9][0-9]*") &&
          scala.util.Try(s.toInt).toOption.exists(_ <= maximum) => Consequence.success(s.toInt)
      case _ => Consequence.configurationInvalid("review package limit must be 1..524288 bytes")
    })
  private val _bounded_contract = "textus-ai.bounded-task.v1"

  private[admission] def packageLimitC(core: ActionCall.Core): Consequence[Int] =
    core.component.map(_.initializationParameters.resolve(maxBytes).map(_.value.getOrElse(maximum)))
      .getOrElse(Consequence.success(maximum))

  private def exact(s: String): Boolean = Option(s).isDefined && s.nonEmpty && java.util.Objects.equals(s.trim, s) && !s.exists(_.isControl)
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
  private def reviewSchema(requireAcceptance: Boolean): Record = Record.dataAuto("type" -> "object",
    "required" -> (Vector("outcome", "summary", "findings") ++ Option.when(requireAcceptance)("acceptance")),
    "properties" -> Record.dataAuto("outcome" -> Record.dataAuto("type" -> "string", "enum" -> Vector("accept", "repair", "decision-needed")), "summary" -> string,
      "findings" -> strings, "acceptance" -> acceptance, "repairFindingSources" -> sourceWrapper,
      "reviewObservations" -> reviewObservations, "issueReport" -> issueReport, "dependencyProposal" -> dependencyProposal,
      "completionConditions" -> completionConditions, "planRevision" -> planRevision))
  private[admission] val schema = reviewSchema(requireAcceptance = false)
  private val acceptanceSchema = reviewSchema(requireAcceptance = true)

  private def normalized(s: String): Consequence[Json] =
    io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(s).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(Consequence.stateConflict(_), Consequence.success)

  private def sameSize(left: Int, right: Int): Boolean = java.lang.Integer.compare(left, right) match {
    case 0 => true
    case _ => false
  }
  private def exactKeys(value: Json, keys: Vector[String]): Boolean =
    value.asObject.exists(o => java.lang.Integer.compare(o.size, keys.size) match {
      case 0 => keys.forall(o.contains)
      case _ => false
    })
  private def field(value: Json, name: String): Json = value.hcursor.downField(name).focus.getOrElse(Json.Null)
  private[admission] def checkFacts(value: Json): Option[Json] = value.asArray.filter(_.nonEmpty).flatMap { checks =>
    if (!checks.forall(c => c.hcursor.downField("identity").focus.flatMap(_.asString).exists(_.nonEmpty))) None
    else Some(Json.fromValues(checks.map(c => Json.obj(Vector("identity", "termination", "exitCode", "sbt")
      .map(k => k -> field(c, k))*))))
  }
  private[admission] def compactIssues(value: Json): Option[Json] = {
    if (value.isNull) Some(Json.Null)
    else value.hcursor.downField("issues").focus.flatMap(_.asArray).flatMap { issues =>
      val projected = issues.map { issue =>
        val summary = issue.hcursor.downField("summary").focus.flatMap(_.asString)
        if (summary.exists(s => s.codePointCount(0, s.length) > 1024)) {
          if (!issue.hcursor.downField("evidenceReference").focus.flatMap(_.asString).exists(_.nonEmpty)) None
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
    if (java.util.Objects.isNull(packageJson) || packageJson.getBytes(UTF_8).length > limit) Consequence.success(false)
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
      val files: Vector[Json] = p.downField("files").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      val names: Vector[String] = files.flatMap(_.hcursor.downField("name").focus.flatMap(_.asString))
      exactKeys(packet, Vector("schema", "continuationId", "resultType", "kind", "purpose",
        "candidate", "review", "files", "completenessSource", "contract", "checksReceipt", "reviewContext", "workResource")) &&
        review.focus.exists(value => exactKeys(value, Vector("diff", "checks", "changedPaths"))) &&
        p.downField("schema").focus.flatMap(_.asString).contains("sm-workflow.supplied-review.v1") &&
        p.downField("workResource").focus.flatMap(_.asString).contains(workResource) &&
        java.util.Objects.equals(p.downField("continuationId").focus.flatMap(_.asString), actual.issuedWorkContext.map(_.continuationId)) &&
        p.downField("resultType").focus.flatMap(_.asString).contains("GoalPhaseWorkResult") &&
        ctx.hcursor.downField("resultType").focus.flatMap(_.asString).contains("GoalPhaseWorkResult") &&
        w.downField("kind").focus.flatMap(_.asString).exists(Set("ReviewStep", "ReviewPhase")) &&
        java.util.Objects.equals(p.downField("kind").focus, w.downField("kind").focus) &&
        java.util.Objects.equals(p.downField("purpose").focus, w.downField("purpose").focus) &&
        java.util.Objects.equals(p.downField("candidate").focus, w.downField("candidate").focus) &&
        Vector("diff", "changedPaths").forall(k => java.util.Objects.equals(field(field(packet, "review"), k), field(actualReview, k))) &&
        checkFacts(field(actualReview, "checks")).contains(field(field(packet, "review"), "checks")) &&
        contract(issued, ctx).contains(field(packet, "contract")) &&
        java.util.Objects.equals(field(packet, "reviewContext"), reviewContext(actualReview)) &&
        w.downField("checksReceipt").focus.flatMap(_.asString).exists(_.nonEmpty) &&
        java.util.Objects.equals(p.downField("checksReceipt").focus, w.downField("checksReceipt").focus) &&
        disposition.downField("placement").focus.flatMap(_.asString).contains("DELEGATED") &&
        disposition.downField("independence").focus.flatMap(_.asString).contains("REQUIRED") &&
        p.downField("completenessSource").focus.flatMap(_.asString).exists(_.trim.nonEmpty) && files.nonEmpty &&
        sameSize(names.size, files.size) && sameSize(names.distinct.size, names.size) && names.forall(_.trim.nonEmpty) &&
        files.forall(f => exactKeys(f, Vector("name", "content")) && f.hcursor.downField("content").focus.flatMap(_.asString).isDefined)
      }
    }
  }

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] = Vector(new Operation(port))
  private final class Operation(port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(SmWorkflowComponent.GoalPhaseServiceService.RunGoalPhaseApiReviewOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (java.util.Objects.isNull(request) || !request.service.contains("GoalPhaseService") || !"runGoalPhaseApiReview".equals(request.operation))
        Consequence.stateConflict("API review requires its exact owning operation")
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseApiReviewInput](request, "input").flatMap { input =>
        if (Option(input).isDefined && Vector(input.sessionId, input.workResource, input.attemptIdentity).forall(exact) && exact(input.packageResource))
          Consequence.success(Action(request, input, port))
        else Consequence.stateConflict("API review input is incomplete")
      }
  }
  private final case class Action(request: Request, input: V.GoalPhaseApiReviewInput,
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = {
      new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = {
        for {
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
          selected = core.copy(executionContext = context)
          actual <- port.apiReviewWorkC(input, selected)
          continuation <- actual.issuedWorkContext.map(_.continuationId).map(Consequence.success)
            .getOrElse(Consequence.stateConflict("review continuation absent"))
          limit <- packageLimitC(selected)
          reference <- NativeWorkflowResourceInput.referenceC(input.packageResource).flatMap { ref =>
            if ("file".equals(ref.scheme)) Consequence.success(ref)
            else Consequence.stateConflict("API review package requires a local file URI")
          }
          result <- acquireAndReviewC(selected.executionContext, reference, limit, actual, input, continuation,
            text => runC(selected, input, continuation, text))
        } yield NativeWorkflowOperationOutput.response(result)
      }
      }
    }
  }
  private[admission] def acquireAndReviewC(context: ExecutionContext, reference: ResourceReference, limit: Int,
    actual: V.GoalPhaseAdvanceResult, input: V.GoalPhaseApiReviewInput, continuation: String,
    run: String => Consequence[V.GoalPhaseApiReviewResult]): Consequence[V.GoalPhaseApiReviewResult] = {
    def declined = Consequence.success(V.GoalPhaseApiReviewResult("DECLINED", input.attemptIdentity,
      continuation, None, None, None, None, "Incomplete, oversized or crossed supplied package", None))
    context.readResourceText(reference, ResourceReadLimits(limit), Some(UTF_8)) match {
      case Consequence.Failure(conclusion) if java.util.Objects.equals(conclusion.observation.taxonomy, Taxonomy.resourceInvalid) &&
          conclusion.observation.getMessage.contains("resource exceeds the runtime byte limit") => declined
      case Consequence.Failure(conclusion) => Consequence.Failure(conclusion)
      case Consequence.Success(text) => packageC(text, actual, input.workResource, limit).flatMap { eligible =>
        if (eligible) run(text) else declined
      }
    }
  }
  // Only the runner's typed budget refusals are attributable policy blocks.
  private[admission] def blockedReason(conclusion: Conclusion): Option[String] =
    _illegal_operation(conclusion).filter(Set("ai.input-budget", "ai.cost-budget"))

  private def _illegal_operation(conclusion: Conclusion): Option[String] = {
    val observation = conclusion.observation
    val operations = observation.cause.descriptor.facets.collect {
      case Descriptor.Facet.Operation(name) => name
    }
    if (java.util.Objects.equals(observation.taxonomy, Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Illegal)) && java.util.Objects.equals(operations.size, 1))
      operations.headOption
    else None
  }


  // The producer supplies redacted body text; retain only the explicit transport contract.
  private def providerHttp(values: Map[String, String]): Option[Json] = {
    val prefix = "ai.provider.http."
    def lookup(key: String): Option[String] = values.get(key).flatMap(Option(_))
    val status = lookup(prefix + "status").filter(s => s.matches("[1-5][0-9][0-9]"))
      .map(s => "status" -> Json.fromInt(s.toInt))
    val requestId = lookup(prefix + "request-id").filter(s => s.matches("req_[A-Za-z0-9_-]{1,128}"))
      .map(s => "requestId" -> Json.fromString(s))
    val body = lookup(prefix + "body").filter(s => s.getBytes(UTF_8).length <= 1024 * 1024)
      .map(s => "body" -> Json.fromString(s))
    val omitted = lookup(prefix + "body-omitted").filter(s => "size-limit".equals(s))
      .map(s => "bodyOmitted" -> Json.fromString(s))
    val fields = Vector(status, requestId, body, omitted).flatten
    Option.when(fields.nonEmpty)(Json.obj(fields*))
  }

  // Project only closed diagnostic values. The Conclusion's own status is not a
  // provider response, and arbitrary messages and exception facets can contain secrets.
  private val providerFailure = "AI provider request failed: category=(invalid_request|authentication_failed|model_unavailable|timeout|quota_exhausted|rate_limited|unavailable|provider_rejected) status=([1-5][0-9][0-9])".r
  private val _reservation_basis_key = "ai.usage.reservation_basis"
  private val _reservation_bases = Set("verified-daily-reservation", "verified-promotional-credit-reservation")

  private[admission] def failureResult(attempt: String, continuation: String, conclusion: Conclusion): V.GoalPhaseApiReviewResult = {
    val observation = conclusion.observation
    val cause = observation.cause
    val kind = cause.kind.orElse(cause.getException.flatMap(Cause.availabilityKind))
    val provider = observation.getMessage.flatMap {
      case providerFailure(category, status) => Some(Vector(
        "providerCategory" -> Json.fromString(category), "providerStatus" -> Json.fromString(status)))
      case _ => None
    }.getOrElse(Vector.empty)
    val facts = Vector(
      "taxonomyCategory" -> Json.fromString(observation.taxonomy.category.name),
      "taxonomySymptom" -> Json.fromString(observation.taxonomy.symptom.name)) ++
      kind.toVector.map(k => "causeKind" -> Json.fromString(k.name)) ++ provider ++
      observation.properties.get(_reservation_basis_key).filter(_reservation_bases).toVector.map(
        basis => _reservation_basis_key -> Json.fromString(basis)) ++
      providerHttp(observation.properties).toVector.map("providerHttp" -> _)
    val metadata = GoalPhaseNativeMetadata.record(Json.obj(facts*))
    val model = observation.properties.get("ai.execution.model").filter(_safe_provider_fact)
    val observedProvider = observation.properties.get("ai.execution.provider").filter(_safe_provider_fact)
    V.GoalPhaseApiReviewResult("UNCERTAIN", attempt, continuation, None, model, observedProvider, Some(metadata),
      "Provider call did not return a usable result", None)
  }

  private def _safe_provider_fact(text: String): Boolean =
    Option(text).exists(value => value.nonEmpty && value.length <= 256 && !value.exists(_.isControl))

  private def _provider_metadata_json(values: Map[String, String], taskbinding: Map[String, String]): Option[Json] = {
    val facts = values.filter { case (key, text) =>
      (key.startsWith("ai.usage.") || Set("ai.execution.provider", "ai.execution.model", "ai.execution.profile",
        "openai.response_id", "openai.finish_reason", "gemma.response_id")(key)) && _safe_provider_fact(text) &&
        (key != "ai.usage.allowance_admission" || Set("verified-included", "authorized-paid")(text)) &&
        (key != _reservation_basis_key || _reservation_bases(text))
    }
    val projected = (facts ++ taskbinding).toVector.sortBy(_._1).map((k, v) => k -> Json.fromString(v)) ++
      providerHttp(values).toVector.map("providerHttp" -> _)
    Option.when(projected.nonEmpty)(Json.obj(projected*))
  }

  private def _provider_metadata(values: Map[String, String], taskbinding: Map[String, String]): Option[Record] =
    _provider_metadata_json(values, taskbinding).map(GoalPhaseNativeMetadata.record)

  /** Finite observation for native compiler attempt history, with no response body. */
  def boundedRecordEvidence(response: AiRecordResponse): Option[Json] =
    _bounded_observation(response.metadata)

  def boundedFailureEvidence(conclusion: Conclusion): Option[Json] =
    _bounded_observation(conclusion.observation.properties)

  private def _bounded_observation(metadata: Map[String, String]): Option[Json] = {
    val usage = Vector("input_tokens", "output_tokens", "total_tokens", "cached_input_tokens", "reasoning_tokens")
      .flatMap(name => Vector("ai.usage." + name, "ai.usage." + name + "_source"))
    val fields = Vector("ai.execution.provider", "ai.execution.model", "ai.usage.cost_microunits", "ai.usage.tokens", "ai.provider.http.status",
      "ai.provider.http.request-id", "openai.response_id", "gemma.response_id", "ai.usage.allowance_admission",
      _reservation_basis_key) ++ usage
    val values: Map[String, String] = fields.foldLeft(Map.empty[String, String]) { (selected, key) =>
      metadata.get(key).fold(selected)(value => selected.updated(key, value))
    }
    _provider_metadata_json(values, Map.empty)
  }

  private[admission] def providerResult(attempt: String, continuation: String, record: Record,
    model: Option[String], responseMetadata: Map[String, String], taskBinding: Map[String, String] = Map.empty,
    requireAcceptance: Boolean = false): V.GoalPhaseApiReviewResult = {
    val converted = for {
      _ <- GoalPhasePurposeRepair.validatePublicResultC(record)
      _ <- IncrementalSbtDiagnosisRepair.validatePublicResultC(record)
      _ <- GoalPhaseDependencyValidation.validatePublicResultC(record)
      value <- summon[ValueReader[V.GoalPhaseWorkResult]].readC(record)
    } yield value
    val value = converted.toOption
    val valid = value.exists(v => Set("accept", "repair", "decision-needed")(v.outcome) &&
      (!requireAcceptance || v.acceptance.nonEmpty))
    V.GoalPhaseApiReviewResult(if (valid) "COMPLETED" else "FAILED", attempt, continuation,
      Option.when(valid)(value).flatten, model.filter(_safe_provider_fact), responseMetadata.get("ai.execution.provider").filter(_safe_provider_fact),
      _provider_metadata(responseMetadata, taskBinding),
      if (valid) "Submit separately for Admission" else "Provider returned an invalid typed review result or outcome",
      Option.when(!valid)(record))
  }
  private[admission] def runC(core: ActionCall.Core, input: V.GoalPhaseApiReviewInput, continuation: String, packageJson: String): Consequence[V.GoalPhaseApiReviewResult] = {
    val provider = ProviderCall.Core(ProviderRequest("goal-phase-api-review", "review",
      Map("attempt" -> input.attemptIdentity)), core.executionContext, core.component, core.correlationId)
    ProviderEngine.execute(ReviewCall(provider, input, continuation, packageJson))
  }

  /** An exact native work binding and one closed structured-output task. */
  final case class BoundedRecordTask(kind: String, attempt: String, continuation: String,
    inputReference: String, prompt: String, schema: Record) {
    def binding: Map[String, String] = Map("ai.task.contract" -> _bounded_contract, "ai.task.id" -> attempt,
      "ai.task.continuation" -> continuation, "ai.task.input-reference" -> inputReference)
  }

  private val _compiler_position = {
    val integer = Record.dataAuto("type" -> "integer", "minimum" -> 0)
    Record.dataAuto("type" -> "object", "additionalProperties" -> false, "required" -> Vector("line", "character"),
      "properties" -> Record.dataAuto("line" -> integer, "character" -> integer))
  }
  private val _compiler_schema = Record.dataAuto("type" -> "object", "additionalProperties" -> false,
    "required" -> Vector("candidate", "validationReference", "path", "range", "expectedSource", "replacement"),
    "properties" -> Record.dataAuto("candidate" -> string, "validationReference" -> string, "path" -> string,
      "range" -> Record.dataAuto("type" -> "object", "additionalProperties" -> false, "required" -> Vector("start", "end"),
        "properties" -> Record.dataAuto("start" -> _compiler_position, "end" -> _compiler_position)),
      "expectedSource" -> string, "replacement" -> string))
  private val _compiler_selected_schema = Record.dataAuto("type" -> "object", "additionalProperties" -> false,
    "required" -> Vector("replacement"), "properties" -> Record.dataAuto("replacement" -> string))

  /** Only material resolved from the native failure can supply this compiler input. */
  def compilerTask(material: DeterministicRepair.CompilerMaterial, attempt: String, continuation: String,
    validationResource: String, limit: Int): Option[BoundedRecordTask] = {
    val position = material.diagnostic.range.start
    val packet = Json.obj("candidate" -> Json.fromString(material.candidate),
      "validationReference" -> Json.fromString(material.validationReference), "path" -> Json.fromString(material.path),
      "location" -> Json.obj("line" -> Json.fromInt(position.line), "character" -> Json.fromInt(position.character)),
      "diagnostic" -> Json.fromString(material.diagnostic.message), "source" -> Json.fromString(material.document.text))
    val (instructions, supplied, schema) = DeterministicRepair.compilerDiagnosticSelection(material) match {
      case Some((_, selected)) =>
        val instructions = "The compiler selected the exact text shown in selectedSource. " +
          "Return only the replacement text for that span in the replacement field. Preserve the rest of the source."
        (instructions, packet.mapObject(_.add("selectedSource", Json.fromString(selected))), _compiler_selected_schema)
      case None =>
        val instructions = "Echo the candidate, validationReference and path exactly. " +
          "Use zero-based lines and UTF-16 character offsets, with an exclusive range end. " +
          "Select the actual source text to replace and return that exact text, including any quotes, as expectedSource. " +
          "The nonempty replacement range must be localized to the diagnostic."
        (instructions, packet, _compiler_schema)
    }
    val prompt = "Propose a single small Scala compiler correction using only the supplied diagnostic and source. " +
      "Do not use tools or external context. " + instructions + "\n" + supplied.noSpaces
    Option.when(Vector(attempt, continuation, validationResource).forall(exact) && limit > 0 && limit <= maximum &&
      prompt.getBytes(UTF_8).length <= limit)(
      BoundedRecordTask("compiler-repair", attempt, continuation, validationResource, prompt, schema))
  }

  enum BoundedRecordResult {
    case Completed(response: AiRecordResponse)
    case Blocked(reason: String)
    case Declined
    case Failed(conclusion: Conclusion)
    case Uncertain(conclusion: Conclusion)
    case Unbound(model: Option[String], provider: Option[String], metadata: Option[Record])
  }

  def boundedRunner(socket: AiRunnerSocketSet)(using ExecutionContext): Option[AiRunner] =
    socket.aiRunner(ComponentSelector(capabilities = Set(_bounded_contract))).toOption

  /** Call only after any required attempt intent has been retained by the caller. */
  def boundedRecord(runner: AiRunner, task: BoundedRecordTask)(using ExecutionContext): BoundedRecordResult = {
    val binding = task.binding
    _record_c(runner, task.prompt, task.schema, Some("openai"), binding + ("ai.task.kind" -> task.kind)) match {
      case Consequence.Failure(conclusion) if _illegal_operation(conclusion).contains("ai.task.declined") =>
        BoundedRecordResult.Declined
      case Consequence.Failure(conclusion) if _illegal_operation(conclusion).contains("ai.task.failed") =>
        BoundedRecordResult.Failed(conclusion)
      case Consequence.Failure(conclusion) => blockedReason(conclusion)
        .fold(BoundedRecordResult.Uncertain(conclusion))(BoundedRecordResult.Blocked(_))
      case Consequence.Success(response) =>
        val model = response.model.filter(_safe_provider_fact)
        val provider = response.metadata.get("ai.execution.provider").filter(_safe_provider_fact)
        if (binding.forall { case (key, expected) => response.metadata.get(key).contains(expected) } &&
            model.nonEmpty && provider.nonEmpty) BoundedRecordResult.Completed(response)
        else BoundedRecordResult.Unbound(model, provider, _provider_metadata(response.metadata, Map.empty))
    }
  }

  private def _record_c(runner: AiRunner, prompt: String, schema: Record, preference: Option[String],
    metadata: Map[String, String])(using ExecutionContext): Consequence[AiRecordResponse] =
    runner.generateRecord(AiRecordRequest(prompt, schema,
      requirement = AiRunnerRequirement(provider = preference, purpose = Some("software-analysis"), tools = Vector.empty),
      metadata = metadata, properties = Vector(Property("ai.record.retry-limit", "0", None),
        Property("ai.record.capture-provider-http", "true", None))))

  private[admission] def reviewWithSocketC(socket: Option[AiRunnerSocketSet], bounded: Boolean,
    input: V.GoalPhaseApiReviewInput, continuation: String, packageJson: String)(using ExecutionContext): Consequence[V.GoalPhaseApiReviewResult] = {
    def _result_(status: String, message: String) = V.GoalPhaseApiReviewResult(status, input.attemptIdentity,
      continuation, None, None, None, None, message, None)
    val packet = normalized(packageJson).toOption
    val requireAcceptance = packet.exists { packet =>
      val contract = field(packet, "contract")
      field(contract, "effectiveAcceptance").isObject || field(contract, "acceptance").isObject
    }
    val selectedSchema = if (requireAcceptance) acceptanceSchema else schema
    val applicableConditions = for {
      value <- packet
      kind <- field(value, "kind").asString
      effective = field(field(value, "contract"), "effectiveAcceptance")
      boundary <- effective.as[GoalPhaseAcceptanceBoundary.Effective].toOption
      _ <- GoalPhaseAcceptanceBoundary.validateEffectiveC(boundary).toOption
    } yield GoalPhaseAcceptanceBoundary.applicable(boundary, kind).map(_.identity)
    val acceptanceInstructions = if (requireAcceptance) {
      val report = "Return acceptance with conditions and advisories arrays even when no conditions apply to this review.\n"
      val scope = applicableConditions.fold("") { identities =>
        if (identities.isEmpty) "For this issued review, acceptance.conditions must be an empty array.\n"
        else "Only these condition identities may appear in acceptance.conditions for this issued review: " +
          Json.fromValues(identities.map(Json.fromString)).noSpaces + ".\n"
      }
      report + scope
    } else ""
    val prompt = "Independently review only the supplied package. Do not use tools or external context. " +
      reviewResultInstructions + acceptanceInstructions + packageJson
    val runner = socket.flatMap(value => if (bounded) boundedRunner(value) else value.aiRunner().toOption)
    val result = runner match {
      case None => _result_("UNAVAILABLE", if (socket.isEmpty) "AI runner socket unavailable before execution"
        else "AI runner unavailable before execution")
      case Some(value) if bounded =>
        val task = BoundedRecordTask("independent-review", input.attemptIdentity, continuation, input.workResource, prompt, selectedSchema)
        boundedRecord(value, task) match {
          case BoundedRecordResult.Completed(response) => providerResult(input.attemptIdentity, continuation,
            response.record, response.model, response.metadata, task.binding, requireAcceptance)
          case BoundedRecordResult.Blocked(reason) => _result_("BLOCKED", reason)
          case BoundedRecordResult.Declined => _result_("DECLINED", "Bounded record producer declined before dispatch")
          case BoundedRecordResult.Failed(conclusion) =>
            val observed = conclusion.observation.properties
            V.GoalPhaseApiReviewResult("FAILED", input.attemptIdentity, continuation, None,
              observed.get("ai.execution.model").filter(_safe_provider_fact),
              observed.get("ai.execution.provider").filter(_safe_provider_fact),
              boundedFailureEvidence(conclusion).map(GoalPhaseNativeMetadata.record),
              "Bounded record producer reported a final failure", None)
          case BoundedRecordResult.Uncertain(conclusion) => failureResult(input.attemptIdentity, continuation, conclusion)
          case BoundedRecordResult.Unbound(model, provider, metadata) => V.GoalPhaseApiReviewResult("UNCERTAIN",
            input.attemptIdentity, continuation, None, model, provider, metadata,
            "Bounded review response did not match its task binding", None)
        }
      case Some(value) => _record_c(value, prompt, selectedSchema, None, Map.empty) match {
        case Consequence.Failure(conclusion) => blockedReason(conclusion).fold(
          failureResult(input.attemptIdentity, continuation, conclusion))(_result_("BLOCKED", _))
        case Consequence.Success(response) => providerResult(input.attemptIdentity, continuation,
          response.record, response.model, response.metadata, requireAcceptance = requireAcceptance)
      }
    }
    Consequence.success(result)
  }
  private final case class ReviewCall(core: ProviderCall.Core, input: V.GoalPhaseApiReviewInput, continuation: String, packageJson: String)
      extends ProviderCall[V.GoalPhaseApiReviewResult] {
    override def calltreeResultAttributes(result: V.GoalPhaseApiReviewResult): Map[String, String] = Map.empty
    protected def build_Program: ExecUowM[V.GoalPhaseApiReviewResult] = provider_step("goal-phase-api-review", Map.empty) {
      given ExecutionContext = executionContext
      val socket = core.component.flatMap { component =>
        val port: Component.Port = component.port
        port.entries.collectFirst { case value: AiRunnerSocketSet => value }
      }
      reviewWithSocketC(socket, input.boundedTask.getOrElse(false), input, continuation, packageJson)
    }
  }
}
