package org.simplemodeling.textus.smworkflow.caller

import io.circe.{Json, parser}
import org.goldenport.cncf.workflow.WorkflowExecutionHarness as H
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata, GoalPhasePhaseMetrics as M, GoalPhaseWorkComparison as C}
import org.simplemodeling.textus.smworkflow.value as V
import M.given

/** Passive projection of caller-retained execution resources. Nothing here
  * acquires usage, invokes a provider, reads a file, advances a Workflow or
  * certifies Phase-wide coverage from a final invocation.
  * @since   Oct. 29, 2026
  * @version Nov. 26, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeCallerPhaseMetrics {
  /** Destination/inclusion are the host's retained adapter observations, never
    * inferred from model names, localhost or executor placement. Unknown remains
    * explicit. Complete coverage must be established independently for the Phase.
    */
  final case class RetainedExecution(session: H.Session, destination: String, role: String,
    intervalStart: String, intervalEnd: String, inclusion: String = "unknown",
    coveredExecutions: Vector[String] = Vector.empty, completePhaseCoverage: Boolean = false)

  private[smworkflow] def observations(phaseSession: String, retained: Vector[RetainedExecution]): Vector[M.UsageObservation] =
    retained.filter(value => value.session != null && value.session.workReference.handle.instanceIdentity.value == phaseSession)
      .flatMap { value =>
        val reference = value.session.workReference
        value.session.attempts.map { attempt =>
          val execution = reference.continuationId.value + ":attempt:" + attempt.selection.attempt
          val resources = attempt.outcome.resources
          M.UsageObservation("caller-execution:" + execution, 1L, phaseSession, execution,
            if (Set("codex", "local", "other")(value.destination)) value.destination else "unknown", value.role,
            attempt.outcome.executor.map(_.profile.model), value.intervalStart, value.intervalEnd,
            resources.inputTokens, resources.outputTokens, inclusion = value.inclusion,
            coveredExecutions = value.coveredExecutions, coverage = if (value.completePhaseCoverage) "complete" else "partial",
            status = attempt.outcome.status.value)
        }
      }

  /** Resources already retained by the new adapter, with provider model unknown
    * unless separately observed. Execution identity is the real launch, not a role.
    */
  final case class DispatchExecution(issued: V.WorkflowIssuedWorkContext, launchIdentity: String,
    destination: String, role: String, observedModel: Option[String], intervalStart: String, intervalEnd: String,
    inputTokens: Option[Long], outputTokens: Option[Long], inclusion: String = "unknown",
    coveredExecutions: Vector[String] = Vector.empty, completePhaseCoverage: Boolean = false, status: String = "unknown")
  private[smworkflow] def dispatchObservations(phaseSession: String,
    retained: Vector[DispatchExecution]): Vector[M.UsageObservation] = retained.filter(value =>
      value.issued != null && value.issued.runId == phaseSession && value.launchIdentity != null &&
        value.launchIdentity.nonEmpty && NativeWorkflowCallerExecution.validateIssuedC(value.issued).isSuccess).map { value =>
      val execution = value.issued.continuationId + ":launch:" + value.launchIdentity
      M.UsageObservation("caller-dispatch:" + execution, 1L, phaseSession, execution,
        if (Set("codex", "local", "other")(value.destination)) value.destination else "unknown", value.role,
        value.observedModel, value.intervalStart, value.intervalEnd, value.inputTokens, value.outputTokens,
        inclusion = value.inclusion, coveredExecutions = value.coveredExecutions,
        coverage = if (value.completePhaseCoverage) "complete" else "partial", status = value.status)
    }

  private def continuation_(state: Json): Option[String] = {
    val request = state.hcursor.downField("request")
    val issued = request.downField("issuedWorkContext").focus.orElse(request.downField("issued_work_context").focus)
    issued.flatMap(value => value.hcursor.get[String]("continuationId").toOption
      .orElse(value.hcursor.get[String]("continuation_id").toOption)).filter(_.nonEmpty)
  }

  /** Parse a retained dispatch or bounded API observation. The caller supplies
    * the actual retained source and explicitly observed route/mode; selection or
    * reservation metadata is not evidence of a provider or billing route.
    */
  private[smworkflow] def retainedCall(phaseSession: String, source: String, revision: Long, state: Json,
    route: Option[String], mode: Option[String], retry: Option[Boolean] = None): Option[M.CallFact] = {
    val cursor = state.hcursor
    def string_(value: Json, key: String): Option[String] = value.hcursor.get[String](key).toOption.filter(_.nonEmpty)
    def field_(value: Json, key: String): Option[Json] = value.hcursor.downField(key).focus
    val request = cursor.downField("request").focus.getOrElse(Json.obj())
    val issued = field_(request, "issuedWorkContext").orElse(field_(request, "issued_work_context")).getOrElse(Json.obj())
    val continuation = continuation_(state)
    val bound = string_(issued, "runId").orElse(string_(issued, "run_id")).contains(phaseSession) && continuation.nonEmpty
    val status = cursor.get[String]("status").toOption
    val schema = cursor.get[String]("schema").toOption
    val isapi = schema.contains("sm-workflow.api-review-attempt.v1")
    val isdispatch = schema.contains("sm-workflow.caller-dispatch.v1")
    val launch = cursor.downField("launchReturn").focus
    val launched = launch.flatMap { value =>
      val task = string_(value, "task_name")
      val agent = string_(value, "agent_id")
      if (task.nonEmpty && agent.nonEmpty && task != agent) None else task.orElse(agent)
    }.filter(_ => cursor.downField("launch").focus.exists(!_.isNull) && status.exists(Set("STARTED", "RETURNED", "UNCERTAIN")))
    val apiinvocation = cursor.get[String]("invocationIdentity").toOption
    val attempt = cursor.downField("arguments").downField("input").get[String]("attemptIdentity").toOption
    val response = cursor.downField("response").focus.getOrElse(Json.obj())
    val responseattempt = string_(response, "attemptIdentity").orElse(string_(response, "attempt_identity"))
    val responsecontinuation = string_(response, "continuationId").orElse(string_(response, "continuation_id"))
    val responsebound = responseattempt == attempt && attempt.nonEmpty && responsecontinuation == continuation
    val observed = if (!isapi || responsebound) cursor.downField("observed").focus.getOrElse(Json.obj()) else Json.obj()
    val metadata = if (!isapi || responsebound) field_(response, "metadata").getOrElse(Json.obj()) else Json.obj()
    val resources = if (!isapi || responsebound) cursor.downField("resources").focus.getOrElse(Json.obj()) else Json.obj()
    val providerhttp = field_(metadata, "providerHttp").exists(value =>
      value.hcursor.get[Int]("status").toOption.nonEmpty || string_(value, "requestId").nonEmpty)
    val providerresponse = providerhttp || Vector("openai.response_id", "gemma.response_id", "ai.provider.http.request-id")
      .exists(key => string_(metadata, key).nonEmpty) || metadata.hcursor.get[Boolean]("ai.execution.started").toOption.contains(true)
    val responsestatus = Option.when(!isapi || responsebound)(string_(response, "status")).flatten
    val apiconfirmed = apiinvocation.nonEmpty && responsebound && (responsestatus.contains("COMPLETED") || providerresponse)
    val apinotrun = apiinvocation.nonEmpty && responsebound && !providerresponse && responsestatus.exists(Set("BLOCKED", "UNAVAILABLE"))
    val rejected = cursor.downField("rejection").get[Boolean]("executionStarted").toOption.contains(false)
    val execution = if (isdispatch && launched.nonEmpty || isapi && apiconfirmed) "confirmed"
      else if (isapi && apinotrun || isdispatch && rejected && launched.isEmpty) "not-run" else "unknown"
    val attempts = cursor.downField("attempts").focus.flatMap(_.asArray).getOrElse(Vector.empty)
    val candidate = cursor.downField("resolved").get[String]("candidate").toOption
    val rejection = cursor.downField("rejection").focus
    val matchedRejection = attempts.indexWhere(a => rejection.nonEmpty && a.hcursor.downField("reason").focus == rejection)
    val rejectedSlot = if (matchedRejection >= 0) matchedRejection else candidate.flatMap(c =>
      attempts.indexWhere(a => a.hcursor.get[String]("candidate").toOption.contains(c)) match {
        case -1 => None
        case index => Some(index)
      }).getOrElse(attempts.size)
    val actualid = if (isapi) apiinvocation.orElse(attempt.map("unresolved-api:" + _))
      else if (rejected && launched.isEmpty) Some("unstarted:" + rejectedSlot) else launched.orElse(Some("unresolved-dispatch"))
    val identity = for { work <- continuation; id <- actualid } yield work + ":" + id
    def token_(name: String): Option[Long] = {
      val value = field_(metadata, "ai.usage." + name).orElse(field_(resources, name))
      value.flatMap(v => v.asNumber.flatMap(_.toLong).orElse(v.asString.flatMap(s => scala.util.Try(s.toLong).toOption))).filter(_ >= 0)
    }
    def provenance_(name: String): Option[String] = string_(metadata, "ai.usage." + name + "_source")
    val provider = string_(metadata, "ai.execution.provider").orElse(Option.when(!isapi || responsebound)(string_(response, "provider")).flatten).orElse(Option.when(!isapi || responsebound)(string_(observed, "provider")).flatten)
    val model = string_(metadata, "ai.execution.model").orElse(Option.when(!isapi || responsebound)(string_(response, "model")).flatten).orElse(Option.when(!isapi || responsebound)(string_(observed, "model")).flatten)
    val engine = if (isdispatch && launched.nonEmpty) Some("codex") else if (isapi && apiconfirmed) provider else None
    val level = string_(observed, "effort")
    Option.when(bound && (isapi || isdispatch) && source.nonEmpty && revision >= 0)(identity).flatten.map(id => M.CallFact(id, revision, source,
      execution, engine, route, mode, level, retry, Option.when(isdispatch && launched.nonEmpty)(attempts.nonEmpty),
      token_("input_tokens"), token_("output_tokens"), token_("total_tokens"),
      provenance_("input_tokens"), provenance_("output_tokens"), provenance_("total_tokens"),
      provider = provider, phaseSession = Some(phaseSession), model = model,
      outcome = responsestatus.orElse(Option.when(!isapi)(cursor.downField("semanticResult").get[String]("outcome").toOption.orElse(status)).flatten),
      usageContract = string_(metadata, "ai.usage.contract").filter(_ == "input-plus-output")
        .filter(_ => field_(metadata, "ai.usage.input_tokens").nonEmpty && field_(metadata, "ai.usage.output_tokens").nonEmpty),
      continuation = if (isdispatch) continuation.map("dispatch:" + _) else for {
        work <- continuation; id <- attempt
      } yield "api:" + work + ":" + id,
      unresolvedDispatch = if (isdispatch) Option.when(!rejected || launched.nonEmpty)(launched.isEmpty)
        else Option.when(isapi && attempt.nonEmpty && execution != "not-run")(apiinvocation.isEmpty)))
  }

  /** The dispatch prefix records unavailable candidates separately from the
    * actual launch. They are retained as not-run observations, never calls. */
  private[smworkflow] def retainedCalls(phaseSession: String, source: String, revision: Long, state: Json,
    route: Option[String], mode: Option[String]): Vector[M.CallFact] = {
    val attempts = state.hcursor.downField("attempts").focus.flatMap(_.asArray).getOrElse(Vector.empty)
    val skipped = attempts.zipWithIndex.flatMap { case (attempt, index) =>
      Option.when(attempt.hcursor.get[Boolean]("executionStarted").toOption.contains(false)) {
        M.CallFact(continuation_(state).getOrElse("") + ":unstarted:" + index, revision, source, "not-run", None, route, mode,
          None, None, None, None, None, None, None, None, None)
      }
    }
    val main = retainedCall(phaseSession, source, revision, state, route, mode).toVector
    if (main.isEmpty) Vector.empty else skipped.map(_.copy(phaseSession = Some(phaseSession))) ++ main
  }

  final case class ComparableCohort(identity: String, unit: String, workClass: String,
    windowStart: Option[String], windowEnd: String, sourceRevision: String)
  final case class ComparableWork(identity: String, unit: String, workClass: String, outcome: String,
    firstPass: Option[Boolean], startedAt: Option[String], terminalAt: Option[String],
    profiles: Vector[String], executions: Vector[String])

  /** The caller declares a comparable cohort before aggregation and supplies only
    * already-retained work/attempt facts. This never acquires usage or closes work.
    */
  def compare(phaseSession: String, completion: V.GoalPhaseCompletionResult,
    cohort: ComparableCohort, work: Vector[ComparableWork], retained: Vector[RetainedExecution],
    dispatch: Vector[DispatchExecution] = Vector.empty): V.GoalPhaseCompletionResult = {
    val original = completion.metrics.flatMap(record => record.toJsonStringC.toOption
      .flatMap(text => parser.parse(text).toOption).flatMap(json => GoalPhaseNativeMetadata.normalizePublicJson(json).toOption)
      .flatMap(_.as[M.Summary].toOption))
    original.filter(_.phaseSession == phaseSession).map { summary =>
      val resources = summary.usage.flatMap(_.observations) ++ observations(phaseSession, retained) ++ dispatchObservations(phaseSession, dispatch)
      val comparison = C.project(phaseSession, C.Cohort(cohort.identity, cohort.unit, cohort.workClass,
        cohort.windowStart, cohort.windowEnd, cohort.sourceRevision), work.map(w => C.Work(w.identity, w.unit,
          w.workClass, w.outcome, w.firstPass, w.startedAt, w.terminalAt, w.profiles, w.executions)), resources)
      completion.copy(metrics = Some(M.record(summary.copy(comparison = summary.comparison.filterNot(_.cohort.identity == cohort.identity) :+ comparison))))
    }.getOrElse(completion)
  }

  /** Reprojects only a completion with its native retained endpoint record. The
    * caller retains this output alongside its original harness resources. A
    * later recollection uses those same records, not later account usage.
    * Missing/invalid optional metrics leave the native completion unchanged.
    */
  def project(phaseSession: String, completion: V.GoalPhaseCompletionResult,
    retained: Vector[RetainedExecution], dispatch: Vector[DispatchExecution] = Vector.empty,
    callFacts: Vector[M.CallFact] = Vector.empty): V.GoalPhaseCompletionResult = {
    val summary = completion.metrics.flatMap(record => record.toJsonStringC.toOption
      .flatMap(text => parser.parse(text).toOption)
      .flatMap(json => GoalPhaseNativeMetadata.normalizePublicJson(json).toOption)
      .flatMap(_.as[M.Summary].toOption))
    summary.filter(_.phaseSession == phaseSession).map { original =>
      val callerUsage = observations(phaseSession, retained)
      val dispatchUsage = dispatchObservations(phaseSession, dispatch)
      // A harness interval covers the whole harness session. It is a per-launch
      // boundary only when exactly one actual executor was retained.
      val boundedHarness = observations(phaseSession, retained.filter(r => r.session != null &&
        r.session.attempts.size == 1 && r.session.attempts.head.outcome.executor.nonEmpty))
      val nativeSpans = original.operationalTime.toVector.flatMap(_.spans)
      val nativeIds = nativeSpans.filter(_.category == "ai").map(_.identity).toSet
      val harnessIds = boundedHarness.map(_.execution).toSet
      val dispatchIds = dispatchUsage.map(_.execution).toSet
      val sets = Vector(nativeIds, harnessIds, dispatchIds).filter(_.nonEmpty)
      // Different labels across independent sources do not prove different
      // executions. Preserve the already bound native observations in that case.
      val ambiguous = sets.size > 1 && sets.distinct.size > 1
      val additions = if (ambiguous) Vector(M.ObservedSpan("caller-unreconciled", "ai", None, None,
        None, "caller:execution-attribution")) else (boundedHarness ++ dispatchUsage).map { u =>
        M.ObservedSpan(u.execution, "ai", Some(u.intervalStart), Some(u.intervalEnd), Some(u.role), u.identity)
      }.filterNot(s => nativeSpans.exists(n => n.category == s.category && n.identity == s.identity &&
        n.start == s.start && n.end == s.end && n.role == s.role))
      val unbounded = Option.when(retained.exists(r => r.session != null && r.session.attempts.size > 1))(
        M.ObservedSpan("caller-multiple-attempts", "ai", None, None, None, "caller:session-boundaries"))
      val time = M.operationalTime(original.start.map(_.observedAt), Some(original.end.observedAt), nativeSpans ++ additions ++ unbounded)
      val observed = M.summarize(phaseSession, original.start, original.end,
        original.usage.flatMap(_.observations) ++ callerUsage ++ dispatchUsage, original.profile)
        .copy(issuedProfiles = original.issuedProfiles, comparison = original.comparison, retainedObservations = original.retainedObservations,
          calls = if (callFacts.nonEmpty) Some(M.calls(original.calls.toVector.flatMap(_.observations) ++ callFacts.filter(_.phaseSession.contains(phaseSession)))) else original.calls,
          deterministicRepairs = original.deterministicRepairs, operationalTime = Some(time),
          operations = original.operations)
      completion.copy(metrics = Some(M.record(observed)))
    }.getOrElse(completion)
  }
}
