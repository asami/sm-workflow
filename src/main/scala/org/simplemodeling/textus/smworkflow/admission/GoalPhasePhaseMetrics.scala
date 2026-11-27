package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import scala.util.control.NonFatal
import GoalPhaseWorkComparison as C
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.resource.{ResourceTreeEntry, ResourceTreeQueryLimits, ResourceTreeSelection}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.workspace.{FileReadSnapshotResult, FileTarget, WorkspaceId}
import org.goldenport.record.Record

/** Passive endpoint observations in the owning native records. Metrics never
  * select work, authorize an effect, acquire usage or change a close outcome.
  * @since   Oct. 29, 2026
  * @version Nov. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePhaseMetrics {
  val rule = "physical-lf-with-final-unterminated-line.v1"
  final case class Scope(workspace: String, root: String, includes: Vector[String],
    excludes: Vector[String], suffixes: Vector[String], countingRule: String = rule)
  final case class Endpoint(scope: Scope, status: String, lines: Option[Long], files: Option[Long],
    bytes: Option[Long], observedAt: String, sourceHead: String, sourceTree: String, reason: String,
    limits: Option[ResourceTreeQueryLimits] = None)
  /** A retained observation, never a model/AI estimate. coveredExecutions is an
    * explicit producer inclusion contract; absent inclusion knowledge is unsafe.
    */
  final case class UsageObservation(identity: String, revision: Long, phaseSession: String,
    execution: String, destination: String, role: String, model: Option[String],
    intervalStart: String, intervalEnd: String, inputTokens: Option[Long], outputTokens: Option[Long],
    cachedInputTokens: Option[Long] = None, reasoningOutputTokens: Option[Long] = None,
    inclusion: String = "unknown", coveredExecutions: Vector[String] = Vector.empty,
    coverage: String = "partial", status: String = "unknown")
  final case class Usage(destination: String, coverage: String, reason: String,
    inputTokens: Option[Long], outputTokens: Option[Long], cachedInputTokens: Option[Long],
    reasoningOutputTokens: Option[Long], totalTokens: Option[Long], observations: Vector[UsageObservation],
    tokensPerNetSourceLine: Option[BigDecimal], ratioReason: String)
  /** Individual observations retain their original provenance and unknown values. */
  final case class CallFact(invocation: String, revision: Long, source: String, execution: String,
    engine: Option[String], route: Option[String], mode: Option[String], level: Option[String],
    retry: Option[Boolean], fallback: Option[Boolean], input: Option[Long], output: Option[Long],
    total: Option[Long], inputSource: Option[String], outputSource: Option[String], totalSource: Option[String],
    provenance: Vector[String] = Vector.empty, provider: Option[String] = None, phaseSession: Option[String] = None,
    model: Option[String] = None, outcome: Option[String] = None, conflictingFields: Vector[String] = Vector.empty,
    usageContract: Option[String] = None, continuation: Option[String] = None, unresolvedDispatch: Option[Boolean] = None,
    dispatchKind: Option[String] = None, nonstartReason: Option[String] = None)
  final case class TokenCoverage(value: Option[Long], observed: Int, executions: Int, overflow: Boolean)
  final case class CallGroup(engine: Option[String], route: Option[String], mode: Option[String], level: Option[String],
    executions: Int, retries: Int, fallbacks: Int, input: TokenCoverage, output: TokenCoverage, total: TokenCoverage,
    provider: Option[String] = None, unknownRetries: Int = 0, unknownFallbacks: Int = 0)
  final case class CallConflict(invocation: String, fields: Vector[String])
  final case class Calls(groups: Vector[CallGroup], session: CallGroup, unknownExecutions: Int,
    notRun: Int, conflictingInvocations: Vector[String], observations: Vector[CallFact],
    conflicts: Vector[CallConflict] = Vector.empty)
  given Encoder[CallFact] = deriveEncoder[CallFact].mapJson(_.mapObject { fields =>
    Vector("dispatchKind", "nonstartReason").foldLeft(fields) { (prior, name) =>
      if (prior(name).exists(_.isNull)) prior.remove(name) else prior
    }
  })
  given Decoder[CallFact] = deriveDecoder
  given Encoder[TokenCoverage] = deriveEncoder
  given Decoder[TokenCoverage] = deriveDecoder
  given Encoder[CallGroup] = deriveEncoder
  given Decoder[CallGroup] = deriveDecoder
  given Encoder[CallConflict] = deriveEncoder
  given Decoder[CallConflict] = deriveDecoder
  given Encoder[Calls] = deriveEncoder
  given Decoder[Calls] = deriveDecoder

  /** An application is identified by its native effect, never by file or rule.
    * Each positive condition requires its own retained evidence. Unknown is not
    * a negative observation and cannot establish either a repair or a zero. */
  final case class RepairApplication(identity: String, tool: Option[String], origin: String,
    changed: Option[Boolean], revalidated: Option[Boolean], retained: Option[Boolean],
    rolledBack: Option[Boolean], candidate: Option[String], validation: Option[String],
    sources: Vector[String])
  final case class RepairTool(tool: String, count: Option[Int], coverage: String, reason: String)
  final case class RepairZeroPass(identity: String, tool: String, step: String, sourceRevision: String,
    index: String, paths: Vector[String], candidate: String, validation: String, commit: String)
  final case class Repairs(tools: Vector[RepairTool], observations: Vector[RepairApplication],
    coverage: String, reason: String, zeroPasses: Option[Vector[RepairZeroPass]] = None)
  given Encoder[RepairApplication] = deriveEncoder
  given Decoder[RepairApplication] = deriveDecoder
  given Encoder[RepairTool] = deriveEncoder
  given Decoder[RepairTool] = deriveDecoder
  given Encoder[RepairZeroPass] = deriveEncoder
  given Decoder[RepairZeroPass] = deriveDecoder
  given Encoder[Repairs] = deriveEncoder[Repairs].mapJson(_.mapObject { fields =>
    if (fields("zeroPasses").exists(_.isNull)) fields.remove("zeroPasses") else fields
  })
  given Decoder[Repairs] = deriveDecoder

  /** Boundaries are retained observations, not estimates of process time. */
  final case class ObservedSpan(identity: String, category: String, start: Option[String], end: Option[String],
    role: Option[String], source: String, processMillis: Option[Long] = None)
  final case class SpanTotal(category: String, millis: Option[Long], completeSpans: Int,
    incompleteSpans: Int, conflictingIdentities: Vector[String], coverage: String)
  final case class OperationalTime(elapsedMillis: Option[Long], elapsedReason: String,
    spans: Vector[ObservedSpan], totals: Vector[SpanTotal])
  given Encoder[ObservedSpan] = deriveEncoder
  given Decoder[ObservedSpan] = deriveDecoder
  given Encoder[SpanTotal] = deriveEncoder
  given Decoder[SpanTotal] = deriveDecoder
  given Encoder[OperationalTime] = deriveEncoder
  given Decoder[OperationalTime] = deriveDecoder

  private def spanMillis(start: Option[String], end: Option[String]): Option[Long] = for {
    a <- start.flatMap(s => scala.util.Try(java.time.Instant.parse(s)).toOption)
    b <- end.flatMap(s => scala.util.Try(java.time.Instant.parse(s)).toOption)
    millis <- Option.when(!b.isBefore(a))(scala.util.Try(java.time.Duration.between(a, b).toMillis).toOption).flatten
  } yield millis

  def operationalTime(start: Option[String], end: Option[String], raw: Vector[ObservedSpan]): OperationalTime = {
    val valid = raw.filter(s => s.identity.nonEmpty && s.source.nonEmpty &&
      Set("ai", "build-test", "human-wait")(s.category))
    val grouped = valid.groupBy(s => (s.category, s.identity))
    val conflicts = grouped.collect { case (key, rows) if rows.distinct.size != 1 => key }.toSet
    val unique = grouped.toVector.filterNot(x => conflicts(x._1)).map(_._2.head).sortBy(s => (s.category, s.identity))
    val totals = Vector("ai", "build-test", "human-wait").map { category =>
      val selected = unique.filter(_.category == category)
      val values = selected.flatMap(s => spanMillis(s.start, s.end).orElse(
        Option.when(s.category == "build-test")(s.processMillis.filter(_ >= 0)).flatten))
      val conflicting = conflicts.collect { case (`category`, id) => id }.toVector.sorted
      val total = scala.util.Try(values.foldLeft(0L)(Math.addExact)).toOption.filter(_ => values.nonEmpty)
      SpanTotal(category, total, values.size, selected.size - values.size, conflicting,
        if (total.isEmpty) "unavailable" else "partial")
    }
    val elapsed = spanMillis(start, end)
    OperationalTime(elapsed, if (elapsed.nonEmpty) "Original retained endpoint boundaries" else "Original endpoint boundary unavailable or incompatible",
      valid.distinct.sortBy(s => (s.category, s.identity, s.source)), totals)
  }

  /** completeTools is explicit evidence of complete route observation, not the
    * set of tools seen. Conflicts and unknown origins remain partial. */
  def repairs(raw: Vector[RepairApplication], completeTools: Set[String]): Repairs = {
    val tools = Vector("scalafix", "metals", "scalafmt", "built-in")
    val rows = raw.filter(x => x.identity.nonEmpty && x.origin.nonEmpty && x.sources.nonEmpty)
    val grouped = rows.groupBy(_.identity)
    val unique = grouped.values.filter(_.distinct.size == 1).map(_.head).toVector
    val conflict = grouped.values.exists(_.distinct.size != 1)
    val unassigned = unique.exists(x => x.tool.forall(t => !tools.contains(t))) || conflict || rows.size != raw.size
    def eligible(x: RepairApplication): Boolean = x.changed.contains(true) && x.revalidated.contains(true) &&
      x.retained.contains(true) && x.rolledBack.contains(false) && x.candidate.nonEmpty && x.validation.nonEmpty
    def uncertain(x: RepairApplication): Boolean = !x.changed.contains(false) && !x.revalidated.contains(false) &&
      !x.retained.contains(false) && !x.rolledBack.contains(true) && !eligible(x)
    val results = tools.map { tool =>
      val selected = unique.filter(_.tool.contains(tool))
      val count = selected.count(eligible)
      val partial = unassigned || selected.exists(uncertain) || !completeTools(tool)
      val available = count > 0 || (!partial && completeTools(tool))
      RepairTool(tool, Option.when(available)(count), if (!available) "unavailable" else if (partial) "partial" else "complete",
        if (partial) "Only proven applications are counted; other route or retention evidence is incomplete"
        else "Complete retained route evidence, including observed zero")
    }
    val coverage = if (results.forall(_.coverage == "complete")) "complete"
      else if (results.exists(_.count.nonEmpty)) "partial" else "unavailable"
    Repairs(results, rows.distinct.sortBy(x => (x.identity, x.origin)), coverage,
      "Applications require changed bytes, successful normal revalidation and retained candidate evidence; unknown and rollback are excluded")
  }

  /** A check observation is bound to the selected native validation and check.
    * Missing outcomes never imply a skipped or successful check. */
  final case class ValidationObservation(identity: String, kind: String, started: Option[Boolean],
    outcome: String, reason: Option[String], candidates: Option[Int], selectedCases: Option[Int],
    caseCoverage: Option[String])
  final case class DiagnosticObservation(identity: String, tool: String, outcome: Option[String], selected: Option[Boolean], started: Option[Boolean])
  final case class RouteFact(phaseSession: String, continuation: String, owner: String, revision: Long, source: String,
    role: String, route: String, disposition: String, reason: String, stepIdentity: Option[String] = None)
  given Encoder[RouteFact] = deriveEncoder
  given Decoder[RouteFact] = deriveDecoder
  final case class CountRow(identity: String, unit: String, total: Option[Int], selected: Option[Int],
    excluded: Option[Int], started: Option[Int], completed: Option[Int], failed: Option[Int], skipped: Option[Int],
    unknown: Option[Int], coverage: String, reason: String)
  given Encoder[ValidationObservation] = deriveEncoder
  given Decoder[ValidationObservation] = deriveDecoder
  given Encoder[CountRow] = deriveEncoder
  given Decoder[CountRow] = deriveDecoder

  def validationCounts(raw: Vector[ValidationObservation]): Vector[CountRow] = {
    val kinds = Vector("compile", "admission", "test-only", "full", "fix-check")
    val groups = raw.filter(x => x.identity.nonEmpty && kinds.contains(x.kind)).groupBy(_.identity)
    val unique = groups.values.filter(_.distinct.size == 1).map(_.head).toVector
    val conflicting = groups.values.exists(_.distinct.size != 1)
    kinds.flatMap { kind =>
      val items = unique.filter(_.kind == kind)
      val seen = items.nonEmpty
      def count_(n: Int, allKnown: Boolean): Option[Int] = Option.when(seen && (n > 0 || allKnown))(n)
      val known = items.forall(_.outcome != "unknown")
      val reasons = items.flatMap(_.reason).filter(value => value.matches("[A-Za-z][A-Za-z0-9-]{0,63}"))
        .groupBy(identity).toVector.sortBy(_._1).map((name, values) => name + "=" + values.size).mkString(", ")
      val reason = (if (conflicting) "Conflicting retained identities omitted; coverage incomplete"
        else "Observed selected checks only; unavailable plan and missing receipts remain unknown") +
        (if (reasons.isEmpty) "" else "; " + reasons)
      val checks = CountRow("validation:" + kind, "check", None, Option.when(seen)(items.size), None,
        count_(items.count(_.started.contains(true)), items.forall(_.started.nonEmpty)),
        count_(items.count(_.outcome == "succeeded"), known),
        count_(items.count(_.outcome == "failed"), known),
        count_(items.count(_.outcome == "not-executed"), known),
        Option.when(seen)(items.count(x => x.outcome == "unknown" || x.started.isEmpty)),
        if (seen) "partial" else "unavailable", reason)
      val observed = items.filter(x => x.candidates.nonEmpty && x.selectedCases.nonEmpty && x.caseCoverage.nonEmpty)
      def cases_(n: Int): Option[Int] = Option.when(observed.nonEmpty &&
        (n > 0 || observed.forall(_.caseCoverage.contains("complete"))))(n)
      val cases = CountRow("validation:" + kind + ":cases", "case",
        cases_(observed.flatMap(_.candidates).sum),
        cases_(observed.flatMap(_.selectedCases).sum),
        cases_(observed.map(x => x.candidates.get - x.selectedCases.get).sum),
        None, None, None, None, None,
        if (observed.isEmpty) "unavailable" else "partial",
        "Known same-pass case inventory subtotal; missing or partial discovery is not zero")
      Vector(checks, cases)
    }
  }

  /** Display rows for all known routes even when their owners retained no
    * selection observation. Retained edits are a distinct unit from checks. */
  def selectionRows(validation: Vector[CountRow], repairs: Option[Repairs],
    rawPreviews: Vector[GoalPhaseNativeScalaCleanup.PreviewObservation] = Vector.empty,
    rawDiagnostics: Vector[DiagnosticObservation] = Vector.empty): Vector[CountRow] = {
    val ai = Vector("openai-daily", "openai-promotional-credit", "codex", "other").map { route =>
      CountRow("ai:" + route, "logical-work", None, None, None, None, None, None, None, None,
        "unavailable", "Selection denominator and disposition are not retained by this projection")
    }
    val edits = Vector("scalafix", "metals", "scalafmt", "built-in").map { tool =>
      val retained = repairs.toVector.flatMap(_.tools).find(_.tool == tool)
      CountRow("repair:" + tool, "edit", None, None, None, None, retained.flatMap(_.count), None, None, None,
        retained.map(_.coverage).getOrElse("unavailable"), retained.map(_.reason).getOrElse("No retained tool observation"))
    }
    val grouped = rawPreviews.groupBy(_.identity)
    val unique = grouped.values.filter(_.distinct.size == 1).map(_.head).toVector
    val previews = Vector("scalafix", "metals", "scalafmt", "built-in").map { tool =>
      val rows = unique.filter(_.tool == tool)
      val seen = rows.nonEmpty
      val complete = rows.count(row => Set("applied", "unchanged")(row.outcome))
      val failed = rows.count(_.outcome == "failed")
      val unknown = rows.count(row => !Set("applied", "unchanged", "failed")(row.outcome))
      def known_(n: Int): Option[Int] = Option.when(seen && (n > 0 || unknown == 0))(n)
      CountRow("repair:" + tool + ":preview", "source-file", None, Option.when(seen)(rows.size), None,
        None, known_(complete), known_(failed), None, Option.when(seen)(unknown),
        if (seen) "partial" else "unavailable",
        "Retained preview outcomes; missing selections, starts and full coverage remain unknown")
    }
    val diagnostics = rawDiagnostics.groupBy(_.identity).values.filter(_.distinct.size == 1).map(_.head).toVector
    val toolDiagnostics = Vector("scalafix", "metals", "scalafmt", "built-in").map { tool =>
      val rows = diagnostics.filter(_.tool == tool)
      val seen = rows.nonEmpty
      val selected = rows.count(_.selected.contains(true))
      val excluded = rows.count(_.selected.contains(false))
      val unknown = rows.size - selected - excluded
      def count_(value: Int): Option[Int] = Option.when(seen && (value > 0 || unknown == 0))(value)
      val reasons = rows.flatMap(_.outcome).groupBy(identity).toVector.sortBy(_._1)
        .map((name, values) => name + "=" + values.size).mkString(", ")
      CountRow("repair:" + tool + ":diagnostic", "diagnostic", Option.when(seen)(rows.size),
        count_(selected), count_(excluded),
        Option.when(rows.exists(_.started.contains(true)))(rows.count(_.started.contains(true))),
        count_(rows.count(row => row.selected.contains(true) && row.outcome.exists(Set("completed", "no-action")))),
        count_(rows.count(_.outcome.exists(Set("timed-out", "incompatible")))), None, Option.when(seen)(unknown),
        if (seen) "partial" else "unavailable",
        "Observed diagnostic attempts only; no-action is not an accepted edit; " + reasons)
    }
    val zeroPasses = repairs.toVector.flatMap(_.zeroPasses.toVector.flatten).groupBy(_.identity).values
      .filter(_.distinct.size == 1).map(_.head).toVector.sortBy(_.identity).map { pass =>
        CountRow("repair:" + pass.tool + ":pass:" + pass.identity, "edit", Some(0), Some(0),
          Some(0), Some(0), Some(0), Some(0), Some(0), Some(0), "complete",
          "Observed unchanged bounded pass for step " + pass.step + ", source revision " + pass.sourceRevision +
            ", index " + pass.index + ", paths " + pass.paths.mkString(", "))
      }
    ai ++ edits ++ zeroPasses ++ previews ++ toolDiagnostics ++ validation
  }

  /** Resolve the latest retained decision per issued work, owner and route, retaining
    * ambiguity instead of attributing conflicting same-revision decisions. */
  def withRouteRows(rows: Vector[CountRow], raw: Vector[RouteFact]): Vector[CountRow] = {
    val valid = raw.filter(x => x.continuation.nonEmpty && x.source.nonEmpty && x.revision >= 0 &&
      Set("dispatch", "api")(x.owner) && Set("selected", "excluded", "unknown")(x.disposition) &&
      Set("codex", "openai-daily", "openai-promotional-credit", "openai-unspecified")(x.route))
    val resolved = valid.groupBy(x => (x.phaseSession, x.continuation, x.owner, x.route)).values.flatMap { values =>
      val latest = values.filter(_.revision == values.map(_.revision).max)
      val distinct = latest.map(x => (x.role, x.route, x.disposition, x.reason)).distinct
      if (distinct.size == 1) Vector(latest.head)
      else latest.map(_.copy(disposition = "unknown", reason = "conflicting-decision")).groupBy(_.route).values.map(_.head).toVector
    }.toVector
    val routes = Vector("codex", "openai-daily", "openai-promotional-credit", "openai-unspecified")
    def role_(kind: String): String = kind match {
      case "ReviewStep" | "ReviewPhase" | "Review" => "review"
      case "RepairStep" | "Repair" => "repair"
      case "ImplementStep" | "Implement" | "DeliveryAcceptance" => "implementation"
      case _ => "other"
    }
    val routeRows = routes.flatMap(route => (Vector(None) ++ Vector("review", "repair", "implementation", "other").map(Some(_))).map { role =>
      val facts = resolved.filter(x => x.route == route && role.forall(_ == role_(x.role)))
      val known = facts.forall(_.disposition != "unknown")
      def number_(value: Int): Option[Int] = Option.when(facts.nonEmpty && (value > 0 || known))(value)
      val reasons = facts.map(_.reason).filter(_.matches("[a-z][a-z0-9-]{0,63}"))
        .groupBy(identity).toVector.sortBy(_._1).map((name, values) => name + "=" + values.size).mkString(", ")
      CountRow("ai:" + route + role.map(":" + _).getOrElse(""), "logical-work", Option.when(facts.nonEmpty)(facts.size),
        number_(facts.count(_.disposition == "selected")), number_(facts.count(_.disposition == "excluded")),
        None, None, None, None, Option.when(facts.nonEmpty)(facts.count(_.disposition == "unknown")),
        if (facts.nonEmpty) "partial" else "unavailable",
        "Observed route decisions; unobserved work and untraversed alternatives remain unknown" +
          (if (reasons.isEmpty) "" else "; " + reasons))
    })
    rows.filterNot(row => row.unit == "logical-work" && routeRows.exists(_.identity == row.identity)) ++ routeRows
  }

  /** Invocation facts remain distinct from logical routing decisions. Only the
    * explicit route carried by the retained adapter may assign an invocation. */
  def withCallRows(rows: Vector[CountRow], calls: Option[Calls]): Vector[CountRow] = {
    val mapping = Vector("openai-daily" -> Set("openai-daily", "daily"),
      "openai-promotional-credit" -> Set("openai-promotional-credit"), "codex" -> Set("codex"), "other" -> Set("other"))
    val raw = calls.toVector.flatMap(_.observations)
    val ambiguous = raw.groupBy(_.invocation).collect { case (id, facts)
      if facts.flatMap(_.route).distinct.size > 1 || facts.flatMap(_.dispatchKind).distinct.size > 1 => id }.toSet ++
      calls.toVector.flatMap(_.conflictingInvocations)
    val observed = mapping.map { (route, names) =>
      val associated = raw.filter(f => if (route == "codex") f.dispatchKind.contains("codex") else f.route.exists(names))
      val facts = associated.filterNot(f => ambiguous(f.invocation))
      val aggregate = Option.when(facts.nonEmpty)(GoalPhasePhaseMetrics.calls(facts))
      val started = aggregate.map(_.session.executions)
      val notrun = aggregate.map(_.notRun)
      val unknownConflicts = associated.filter(f => ambiguous(f.invocation)).map(_.invocation).distinct.size
      val resolved = resolveCalls(facts)._2.map(_._2)
      def terminal(f: CallFact): Option[Boolean] = f.outcome.flatMap {
        case "COMPLETED" | "completed" => Some(true)
        case "FAILED" | "failed" => Some(false)
        case _ => None
      }
      val startedFacts = resolved.filter(_.execution == "confirmed")
      val unknownOutcomes = startedFacts.count(f => terminal(f).isEmpty)
      val knownOutcomes = unknownOutcomes == 0 && unknownConflicts == 0 && resolved.forall(_.execution != "unknown")
      def terminalCount(n: Int): Option[Int] = Option.when(startedFacts.nonEmpty && (n > 0 || knownOutcomes))(n)
      val unknown = Option.when(aggregate.nonEmpty || unknownConflicts > 0)(aggregate.fold(0)(_.unknownExecutions) + unknownConflicts + unknownOutcomes)
      val reasons = facts.groupBy(_.invocation).values.flatMap { values =>
        values.flatMap(_.nonstartReason).filter(Set("unavailable", "declined", "budget-refused")).distinct match {
          case Vector(reason) => Some(reason)
          case _ => None
        }
      }.toVector.groupBy(identity).toVector.sortBy(_._1).map((name, values) => name + "=" + values.size).mkString(", ")
      CountRow("ai:" + route + ":invocations", "invocation", None, None, None,
        started, terminalCount(startedFacts.count(f => terminal(f).contains(true))),
        terminalCount(startedFacts.count(f => terminal(f).contains(false))), notrun, unknown, if (aggregate.nonEmpty || unknownConflicts > 0) "partial" else "unavailable",
        "Attributed invocation observations only; conflicting route evidence remains unknown and routing selection is separate" +
          (if (reasons.isEmpty) "" else "; " + reasons))
    }
    rows.filterNot(_.identity.endsWith(":invocations")) ++ observed
  }

  final case class Summary(schemaVersion: String, phaseSession: String, start: Option[Endpoint], end: Endpoint,
    netSourceLines: Option[Long], netReason: String, usage: Vector[Usage], profile: Option[GoalPhaseOperatingProfiles.Selection],
    issuedProfiles: Vector[GoalPhaseOperatingProfiles.Capture] = Vector.empty,
    comparison: Vector[GoalPhaseWorkComparison.Summary] = Vector.empty,
    retainedObservations: Option[GoalPhaseRetainedObservations.Retained] = None, calls: Option[Calls] = None,
    deterministicRepairs: Option[Repairs] = None, operationalTime: Option[OperationalTime] = None,
    operations: Option[C.Operational] = None, selectionCounts: Option[Vector[CountRow]] = None,
    routeObservations: Option[Vector[RouteFact]] = None)

  given Encoder[ResourceTreeQueryLimits] = deriveEncoder
  given Decoder[ResourceTreeQueryLimits] = deriveDecoder
  given Encoder[Scope] = deriveEncoder
  given Decoder[Scope] = deriveDecoder
  given Encoder[Endpoint] = deriveEncoder
  given Decoder[Endpoint] = deriveDecoder
  given Encoder[UsageObservation] = deriveEncoder
  given Decoder[UsageObservation] = deriveDecoder
  given Encoder[Usage] = deriveEncoder
  given Decoder[Usage] = deriveDecoder
  given Encoder[Summary] = deriveEncoder[Summary].mapJson(_.mapObject { fields =>
    val withoutEmpty = if (fields("comparison").exists(_.asArray.exists(_.isEmpty))) fields.remove("comparison") else fields
    val withoutRetained = if (withoutEmpty("retainedObservations").exists(_.isNull)) withoutEmpty.remove("retainedObservations") else withoutEmpty
    val withoutCalls = if (withoutRetained("calls").exists(_.isNull)) withoutRetained.remove("calls") else withoutRetained
    val withoutRepairs = if (withoutCalls("deterministicRepairs").exists(_.isNull)) withoutCalls.remove("deterministicRepairs") else withoutCalls
    val withoutTime = if (withoutRepairs("operationalTime").exists(_.isNull)) withoutRepairs.remove("operationalTime") else withoutRepairs
    val withoutOperations = if (withoutTime("operations").exists(_.isNull)) withoutTime.remove("operations") else withoutTime
    val withoutCounts = if (withoutOperations("selectionCounts").exists(_.isNull)) withoutOperations.remove("selectionCounts") else withoutOperations
    if (withoutCounts("routeObservations").exists(_.isNull)) withoutCounts.remove("routeObservations") else withoutCounts
  })
  private val _summary_decoder: Decoder[Summary] = deriveDecoder
  given Decoder[Summary] = Decoder.instance { cursor =>
    val normalized = cursor.value.mapObject(fields =>
      {
        val comparison = if (fields.contains("comparison")) fields else fields.add("comparison", Json.arr())
        if (comparison.contains("calls")) comparison else comparison.add("calls", Json.Null)
      })
    _summary_decoder.decodeJson(normalized)
  }

  private val _suffixes = Vector(".scala", ".java", ".sbt", ".py", ".sh", ".cml", ".js", ".mjs", ".ts", ".tsx", ".jsx", ".go", ".rs", ".c", ".h", ".cpp", ".kt")
  private val _excludes = Vector(".git", "target", "project/target", "project/project", ".bloop", ".metals",
    ".codex-workflow", "node_modules", "vendor", "evidence", "src/main/generated", "src/test/generated",
    "src/main/scala/generated", "src/test/scala/generated", "src/main/vendor", "src/test/vendor", "scripts/vendor")

  /** Reuse the declared authored inventory as roots, retaining rules rather than
    * initial filenames. Suffixes exclude documentation/catalog artifacts.
    */
  def scope(d: GoalPhaseNativeRun.Declaration): Scope = Scope("sm-workflow-repository", d.target.root.toString,
    if (d.candidatePaths.nonEmpty) d.candidatePaths else Vector("src", "scripts", "project", "build.sbt"), _excludes, _suffixes)

  def physicalLines(bytes: Vector[Byte]): Long =
    bytes.count(_ == 10.toByte).toLong + (if (bytes.nonEmpty && bytes.last != 10.toByte) 1L else 0L)

  def fromSnapshot(scope: Scope, at: String, source: GoalPhaseOrderedProgression.Snapshot,
    snapshot: FileReadSnapshotResult): Endpoint = {
    val selection = ResourceTreeSelection(scope.includes, scope.excludes, scope.suffixes)
    if (snapshot.target.workspaceId.value != scope.workspace || snapshot.selection != selection || scope.countingRule != rule)
      unavailable(scope, at, source, "Managed snapshot target or counting scope differs")
    else Endpoint(scope, "available", Some(snapshot.entries.foldLeft(0L)((sum, entry) => Math.addExact(sum, physicalLines(entry.bytes)))),
      Some(snapshot.entries.size.toLong), Some(snapshot.totalByteSize), at, source.head, source.tree,
      "Complete bounded current workspace selection; not an atomic claim about unrelated build inputs", Some(snapshot.limits))
  }

  def unavailable(scope: Scope, at: String, source: GoalPhaseOrderedProgression.Snapshot, reason: String): Endpoint =
    Endpoint(scope, "unavailable", None, None, None, at, source.head, source.tree, reason.take(2048))

  /** This is the only live acquisition adapter. The two native owners invoke it
    * once when creating their original retained endpoint, never on recollection.
    */
  def capture(core: ActionCall.Core, scope: Scope, source: GoalPhaseOrderedProgression.Snapshot): Endpoint = {
    val at = core.executionContext.clock.instant().toString
    try {
      val acquired = for {
        workspace <- WorkspaceId.parseC(scope.workspace)
        target = FileTarget(workspace)
        selection = ResourceTreeSelection(scope.includes, scope.excludes, scope.suffixes)
        execution = new SnapshotExecution(core)
        snapshot <- execution.runC(execution.prepare(target, selection))
      } yield fromSnapshot(scope, at, source, snapshot)
      acquired.fold(error => unavailable(scope, at, source, "Managed source snapshot unavailable: " + error.toString), identity)
    } catch { case NonFatal(error) => unavailable(scope, at, source, "Managed source snapshot interrupted: " + error.toString) }
  }
  private final class SnapshotExecution(val core: ActionCall.Core) extends ProcedureActionCall {
    def prepare(target: FileTarget, selection: ResourceTreeSelection): ExecUowM[FileReadSnapshotResult] = file_read_snapshot(target, selection)
    def runC(program: ExecUowM[FileReadSnapshotResult]): Consequence[FileReadSnapshotResult] = executeProgram(program)
    def execute(): Consequence[org.goldenport.protocol.operation.OperationResponse] =
      Consequence.stateConflict("Phase source observation is not a public Operation")
  }

  def summarize(session: String, start: Option[Endpoint], end: Endpoint, observations: Vector[UsageObservation],
    profile: Option[GoalPhaseOperatingProfiles.Selection]): Summary = {
    val comparable = start.exists(s => s.status == "available" && s.lines.nonEmpty && end.status == "available" &&
      end.lines.nonEmpty && s.scope == end.scope)
    val net = if (comparable) start.flatMap(_.lines).map(s => end.lines.get - s) else None
    val reason = if (comparable) "Signed end minus start physical source lines"
      else if (start.isEmpty) "Original Phase start was not measured; adoption-time or historical reconstruction is not substituted"
      else if (start.exists(_.scope != end.scope)) "Endpoint scope/counting rules differ"
      else "One or both original endpoints are unavailable"
    Summary("sm-workflow.phase-metrics.v1", session, start, end, net, reason,
      aggregate(session, observations, net, reason), profile)
  }

  def absentCompletion(session: GoalPhaseNativeRun.Session, d: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, source: GoalPhaseOrderedProgression.Snapshot,
    profiles: Vector[GoalPhaseOperatingProfiles.Capture] = Vector.empty): Summary = {
    val started = session.started.receipt.payload.value
    val selected = started.sourceMetrics.map(_.scope).getOrElse(scope(d))
    val end = unavailable(selected, core.executionContext.clock.instant().toString, source,
      "The existing repository closure predates endpoint capture; a later tree is not substituted")
    summarize(session.current.identity.value, started.sourceMetrics, end, Vector.empty, started.operatingProfile)
      .copy(issuedProfiles = profiles.distinct)
  }

  def completed(session: GoalPhaseNativeRun.Session, d: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, source: GoalPhaseOrderedProgression.Snapshot,
    profiles: Vector[GoalPhaseOperatingProfiles.Capture] = Vector.empty): Summary = {
    val started = session.started.receipt.payload.value
    val selected = started.sourceMetrics.map(_.scope).getOrElse(scope(d))
    // This passive projection uses the same retained Session. An unavailable
    // observation cannot change normal completion or become an inferred zero.
    val observed = try {
      GoalPhaseNativeWorkContinuation.bindC(d, session.current.definition)
        .flatMap(_.completionOperationalEvidenceC(session, core)).toOption
    } catch { case NonFatal(_) => None }
    val (deterministic, operations) = observed.getOrElse(repairs(Vector.empty, Set.empty) -> C.NativeOperations())
    val end = capture(core, selected, source)
    summarize(session.current.identity.value, started.sourceMetrics, end, Vector.empty, started.operatingProfile)
      .copy(issuedProfiles = profiles.distinct, retainedObservations = d.retainedObservations,
        operationalTime = Some(operationalTime(started.sourceMetrics.map(_.observedAt), Some(end.observedAt), operations.spans)),
        deterministicRepairs = Some(deterministic), operations = Some(C.operational(operations, deterministic)),
        selectionCounts = Some(withCallRows(selectionRows(validationCounts(operations.validation), Some(deterministic), operations.toolPreviews, operations.toolDiagnostics), None)))
  }

  /** Passive usage aggregation over already acquired attributable observations.
    * No account counter, provider fetch, guessed destination/model or absent=0.
    */
  def aggregate(session: String, raw: Vector[UsageObservation], net: Option[Long], netReason: String): Vector[Usage] = {
    val attributed = raw.filter(o => o.phaseSession == session && o.identity.nonEmpty && o.execution.nonEmpty &&
      Set("codex", "local", "other", "unknown")(o.destination) && o.revision >= 0 &&
      Vector(o.inputTokens, o.outputTokens, o.cachedInputTokens, o.reasoningOutputTokens).forall(_.forall(_ >= 0)))
    // Contradictory retransmissions are unknown, never arbitrary last-writer wins.
    val unique = attributed.groupBy(o => (o.identity, o.revision)).values.flatMap { values =>
      Option.when(values.distinct.size == 1)(values.head)
    }.toVector.groupBy(_.execution).values.flatMap { values =>
      val latest = values.filter(_.revision == values.map(_.revision).max)
      Option.when(latest.distinct.size == 1)(latest.head)
    }.toVector
    val contradictory = unique.size < attributed.map(o => (o.execution, o.revision)).distinct.size
    val groups = Vector("codex", "local") ++ unique.map(_.destination).filterNot(Set("codex", "local")).distinct.sorted
    groups.map { destination =>
      val selected = unique.filter(_.destination == destination)
      val known = selected.filter(o => o.inclusion == "self-only" || o.inclusion == "inclusive")
      val safe = known.filterNot(child => known.exists(parent => parent.execution != child.execution &&
        parent.inclusion == "inclusive" && parent.coveredExecutions.contains(child.execution)))
      // Inclusive totals cannot be partitioned from execution identifiers alone.
      // Omit both surviving parents of shared coverage, even without a child receipt.
      // Keep unrelated confirmed observations; do not subtract guessed intersections.
      val overlapping = safe.filter(o => o.inclusion == "inclusive" && safe.exists(other =>
        other.execution != o.execution && other.inclusion == "inclusive" &&
          o.coveredExecutions.exists(other.coveredExecutions.contains))).map(_.execution).toSet
      val uncertainInclusion = selected.exists(_.inclusion == "unknown") || safe.exists(o =>
        o.inclusion == "inclusive" && o.coveredExecutions.exists(id => unique.exists(x => x.execution == id && x.destination != destination)))
      val nonOverlapping = safe.filterNot(o => overlapping(o.execution))
      val finalValues = if (uncertainInclusion) nonOverlapping.filter(_.inclusion == "self-only") else nonOverlapping
      def total(f: UsageObservation => Option[Long]): Option[Long] = if (finalValues.isEmpty || finalValues.exists(o => f(o).isEmpty)) None
        else scala.util.Try(finalValues.foldLeft(0L)((sum, o) => Math.addExact(sum, f(o).get))).toOption
      val input = total(_.inputTokens); val output = total(_.outputTokens)
      val tokens = for { i <- input; o <- output; n <- scala.util.Try(Math.addExact(i, o)).toOption } yield n
      val complete = finalValues.nonEmpty && !contradictory && !uncertainInclusion && overlapping.isEmpty && selected.size == known.size && finalValues.forall(_.coverage == "complete")
      val coverage = if (tokens.isEmpty && input.isEmpty && output.isEmpty) "unavailable" else if (complete) "complete" else "partial"
      val reason = if (selected.isEmpty) "No retained usage attributable to this Phase and destination; absence is not zero"
        else if (overlapping.nonEmpty) "Overlapping inclusive observations were omitted; only confirmed non-overlapping usage is included and coverage remains incomplete"
        else if (uncertainInclusion) "Only known non-overlapping observations are included; parent/child inclusion remains unconfirmed"
        else if (contradictory) "Conflicting or cumulative observations were omitted; coverage remains partial"
        else if (!complete) "Partial observed usage; unobserved Phase intervals/executions remain unknown"
        else "Producer-confirmed complete Phase coverage with non-overlapping observations"
      val ratio = for { delta <- net.filter(_ > 0); numerator <- tokens } yield BigDecimal(numerator) / BigDecimal(delta)
      val ratioReason = if (net.isEmpty) netReason else if (net.exists(_ <= 0)) "N/A: net source lines are zero or negative"
        else if (tokens.isEmpty) "N/A: attributable input/output usage is unavailable" else if (coverage == "partial") "Partial observed usage per net source line" else "Observed usage per net source line"
      Usage(destination, coverage, reason, input, output, total(_.cachedInputTokens), total(_.reasoningOutputTokens), tokens,
        finalValues.sortBy(o => (o.intervalStart, o.execution, o.revision)), ratio, ratioReason)
    }
  }

  private[admission] def selectUsagePair(revisions: Vector[(Long, Vector[CallFact])],
    value: CallFact => Option[Long], source: CallFact => Option[String]): (Option[Long], Option[String], Boolean, Boolean) = {
    val pairs = revisions.iterator.map { case (_, rows) => rows.flatMap(f => value(f).map(_ -> source(f))).distinct }
      .find(_.nonEmpty).getOrElse(Vector.empty)
    val values = pairs.map(_._1).distinct
    if (values.size > 1) (None, None, true, false)
    else if (values.isEmpty) (None, None, false, false)
    else {
      val sources = pairs.map(_._2).distinct
      (values.headOption, if (sources.size == 1) sources.head else None, false, sources.size > 1)
    }
  }

  /** Resolve stable invocation identities once for all projections. */
  private def resolveCalls(raw: Vector[CallFact]): (Map[String, Vector[CallFact]], Vector[(String, CallFact)]) = {
    val valid = raw.filter(f => f.invocation.nonEmpty && f.source.nonEmpty && f.revision >= 0 &&
      Set("confirmed", "unknown", "not-run")(f.execution) &&
      Vector(f.input, f.output, f.total).forall(_.forall(_ >= 0)))
    // An unresolved dispatch can be joined only when its continuation has exactly
    // one retained launch. Never guess when later attempts make it ambiguous.
    val launches = valid.filter(f => f.continuation.nonEmpty && f.unresolvedDispatch.contains(false))
      .groupBy(_.continuation.get).view.mapValues(_.map(_.invocation).distinct).toMap
    val reconciled = valid.map { f =>
      val candidates = f.continuation.toVector.flatMap(c => launches.getOrElse(c, Vector.empty))
      if (f.unresolvedDispatch.contains(true) && candidates.size == 1) f.copy(invocation = candidates.head) else f
    }
    val byid = reconciled.groupBy(_.invocation)
    val resolved = byid.toVector.map { case (id, facts) =>
      val revisions = facts.groupBy(_.revision).toVector.sortBy(-_._1)
      val latest = revisions.head._2.sortBy(_.source).head
      val conflicts = Vector.newBuilder[String]
      def field_[A](name: String, f: CallFact => Option[A]): Option[A] = {
        revisions.iterator.map { case (_, rows) => rows.flatMap(f).distinct }.find(_.nonEmpty).flatMap { values =>
          if (values.size == 1) values.headOption else { conflicts += name; None }
        }
      }
      val execution = if (facts.exists(_.execution == "confirmed")) "confirmed"
        else if (facts.exists(_.execution == "not-run")) "not-run" else "unknown"
      if (facts.exists(_.execution == "confirmed") && facts.exists(_.execution == "not-run")) conflicts += "execution"
      val phase = field_("phaseSession", _.phaseSession)
      val engine = field_("engine", _.engine)
      val provider = field_("provider", _.provider)
      val model = field_("model", _.model)
      val route = field_("route", _.route)
      val mode = field_("mode", _.mode)
      val level = field_("level", _.level)
      val retry = field_("retry", _.retry)
      val fallback = field_("fallback", _.fallback)
      def usage_(name: String, value: CallFact => Option[Long], source: CallFact => Option[String]): (Option[Long], Option[String]) = {
        val (number, attribution, valueConflict, sourceConflict) = selectUsagePair(revisions, value, source)
        if (valueConflict) conflicts += name
        if (sourceConflict) conflicts += (name + "Source")
        (number, attribution)
      }
      val (input, inputsource) = usage_("input", _.input, _.inputSource)
      val (output, outputsource) = usage_("output", _.output, _.outputSource)
      // Derive only within one observation, before revision merging. This prevents
      // unrelated sources or revisions from supplying the two operands.
      def derived_(f: CallFact): Option[Long] = if (f.usageContract.contains("input-plus-output"))
        for { in <- f.input; out <- f.output; sum <- scala.util.Try(Math.addExact(in, out)).toOption } yield sum
      else None
      val totalPair = field_("total", f => f.total.map(value => (value, f.totalSource))
        .orElse(derived_(f).map(value => (value, Some("derived:input+output")))))
      val total = totalPair.map(_._1)
      val totalsource = totalPair.flatMap(_._2)
      val outcome = field_("outcome", _.outcome)
      val fields = conflicts.result().distinct.sorted
      id -> latest.copy(execution = execution, phaseSession = phase, engine = engine, provider = provider,
        model = model, route = route, mode = mode, level = level, retry = retry, fallback = fallback,
        input = input, output = output, total = total, inputSource = if (input.nonEmpty) inputsource else None, outputSource = if (output.nonEmpty) outputsource else None,
        totalSource = if (total.nonEmpty) totalsource else None, outcome = outcome, conflictingFields = fields,
        provenance = facts.flatMap(f => f.provenance :+ f.source).distinct.sorted)
    }
    (byid, resolved)
  }

  /** Revisions describe one invocation. Retain raw snapshots for existing readers. */
  def calls(raw: Vector[CallFact]): Calls = {
    val valid = raw.filter(f => f.invocation.nonEmpty && f.source.nonEmpty && f.revision >= 0 &&
      Set("confirmed", "unknown", "not-run")(f.execution) &&
      Vector(f.input, f.output, f.total).forall(_.forall(_ >= 0)))
    val (byid, resolved) = resolveCalls(valid)
    val conflicts = resolved.collect { case (id, fact) if fact.conflictingFields.nonEmpty => id }.sorted
    val selected = resolved.map(_._2).sortBy(_.invocation)
    val confirmed = selected.filter(_.execution == "confirmed")
    def group(values: Vector[CallFact], engine: Option[String], route: Option[String], mode: Option[String], level: Option[String], provider: Option[String]): CallGroup = {
      def coverage(f: CallFact => Option[Long]): TokenCoverage = {
        val numbers = values.flatMap(f)
        val sum = scala.util.Try(numbers.foldLeft(0L)(Math.addExact)).toOption
        TokenCoverage(Option.when(numbers.nonEmpty)(sum).flatten, numbers.size, values.size, numbers.nonEmpty && sum.isEmpty)
      }
      val total = coverage(_.total)
      val derivationOverflow = values.exists(f => byid(f.invocation).exists(row => row.total.isEmpty &&
        row.usageContract.contains("input-plus-output") && row.input.nonEmpty && row.output.nonEmpty &&
        scala.util.Try(Math.addExact(row.input.get, row.output.get)).isFailure))
      CallGroup(engine, route, mode, level, values.size, values.count(_.retry.contains(true)),
        values.count(_.fallback.contains(true)), coverage(_.input), coverage(_.output),
        total.copy(overflow = total.overflow || derivationOverflow), provider,
        values.count(_.retry.isEmpty), values.count(_.fallback.isEmpty))
    }
    val groups = confirmed.groupBy(f => (f.engine, f.route, f.mode, f.level, f.provider)).toVector
      .sortBy { case ((engine, route, mode, level, provider), _) => Vector(engine, route, mode, level, provider).map(_.getOrElse("")).mkString("\u0000") }
      .map { case ((engine, route, mode, level, provider), values) => group(values, engine, route, mode, level, provider) }
    Calls(groups, group(confirmed, None, None, None, None, None), selected.count(_.execution == "unknown"),
      selected.count(_.execution == "not-run"), conflicts, valid.distinct.sortBy(f => (f.invocation, f.revision, f.source, f.toString)),
      resolved.collect { case (id, fact) if fact.conflictingFields.nonEmpty => CallConflict(id, fact.conflictingFields) }.sortBy(_.invocation))
  }

  def record(summary: Summary): Record = GoalPhaseNativeMetadata.record(summary.asJson)
  def withMetrics(result: org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult,
    metrics: Option[Summary]): org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult =
    result.copy(completion = result.completion.map(value => value.copy(metrics = metrics.map(record))),
      message = metrics.map(value => result.message + " " + display(value)).getOrElse(result.message))

  def withComparison(summary: Summary, plan: GoalPhaseOrderedProgression.Plan,
    completed: Vector[GoalPhaseOrderedProgression.ClosedStep], requests: Vector[GoalPhaseWorkComparison.NativeWork]): Summary = {
    val configured = summary.retainedObservations
    val bound = configured.flatMap(value => GoalPhaseRetainedObservations.bindC(value, summary.phaseSession, plan, completed, requests).toOption)
    val work = GoalPhaseWorkComparison.native(plan, completed, requests, bound)
    val observations = summary.usage.flatMap(_.observations) ++ bound.toVector.flatMap(_.attempts.flatMap(_.usage))
    val totals = aggregate(summary.phaseSession, observations, summary.netSourceLines, summary.netReason)
    val sources = bound.toVector.flatMap(r => Vector(r.sourceReference) ++ r.attempts.map(_.sourceReference) ++ r.closures.map(_.sourceReference)).distinct
    val spans = bound.toVector.flatMap(_.attempts).map { a =>
      ObservedSpan(a.executionIdentity, "ai", a.startedAt, a.endedAt, a.usage.map(_.role).distinct match {
        case Vector(role) => Some(role)
        case _ => None
      }, a.sourceReference)
    }
    val retainedCalls = bound.toVector.flatMap(_.attempts.flatMap(_.call))
    val combinedCalls = summary.calls.toVector.flatMap(_.observations) ++ retainedCalls
    val callsSummary = if (combinedCalls.nonEmpty) Some(calls(combinedCalls)) else summary.calls
    val routes = (bound.toVector.flatMap(_.routes.toVector.flatten) ++ requests.flatMap(_.callerRouteObservation))
      .distinct.sortBy(r => (r.continuation, r.owner, r.revision, r.source))
    summary.copy(usage = totals, calls = callsSummary, routeObservations = Option.when(routes.nonEmpty)(routes),
      selectionCounts = summary.selectionCounts.map(rows => withRouteRows(withCallRows(rows, callsSummary), routes)), operationalTime = Some(operationalTime(summary.start.map(_.observedAt), Some(summary.end.observedAt),
      summary.operationalTime.toVector.flatMap(_.spans) ++ spans)), comparison = work.map(_.workClass).distinct.sorted.map { cls =>
      val projected = GoalPhaseWorkComparison.project(summary.phaseSession,
        GoalPhaseWorkComparison.Cohort(plan.identity + ":" + cls, "Step", cls, summary.start.map(_.observedAt),
          summary.end.observedAt, summary.end.sourceTree), work, observations)
      projected.copy(limitations = projected.limitations ++ sources.map("Retained caller source: " + _) ++
        Option.when(configured.nonEmpty && bound.isEmpty)("Configured caller observations refused: actual Session/continuation/Step/kind/commit binding differs"))
    })
  }

  def durableEnglish(value: Summary): String = {
    "Phase source accepted; normal local record/commit closure follows. No later synchronization outcome is inferred.\n" +
      "Source revision: " + value.end.sourceHead + "/" + value.end.sourceTree + ". " + display(value) + "\n" +
      value.issuedProfiles.map(p => "Profile " + p.selection.identity + "@" + p.selection.revision +
        "; mapping=" + p.mappingIdentity.getOrElse("unknown") + "@" + p.mappingRevision.getOrElse("unknown") +
        "; logical dispatch=" + p.dispatch.map(_.toString).getOrElse("legacy") +
        "; configured=" + p.configured.map(_.toString).getOrElse("unknown") +
        "; selected=" + p.selected.map(_.toString).getOrElse("caller-retained or unknown") +
        "; provider observations are retained separately per execution").mkString("\n") +
      "\n" + value.usage.flatMap(_.observations).map(o => "Execution " + o.execution + "; role=" + o.role +
        "; model=" + o.model.getOrElse("unknown") + "; destination=" + o.destination + "; interval=" +
        o.intervalStart + ".." + o.intervalEnd + "; coverage=" + o.coverage).mkString("\n") +
      "\nNo benchmark or model-causality claim. Missing original endpoints, usage, timestamps and launch observations remain unavailable.\n"
  }

  def display(value: Summary): String = {
    def number(value: Option[Long]): String = value.map(_.toString).getOrElse("N/A")
    val source = "Source physical lines: start=" + number(value.start.flatMap(_.lines)) +
      "; end=" + number(value.end.lines) + "; net=" + number(value.netSourceLines) + ". "
    source + value.comparison.map(GoalPhaseWorkComparison.english).mkString(" ") + " Issued profiles=" + (if (value.issuedProfiles.isEmpty) "unavailable" else
      value.issuedProfiles.map(p => p.selection.identity + "@" + p.selection.revision).distinct.mkString(",")) + ". " + value.usage.map(u => u.destination + " tokens=" + number(u.totalTokens) + " (" + u.coverage +
      "); tokens/net source line=" + u.tokensPerNetSourceLine.map(_.bigDecimal.toPlainString).getOrElse("N/A") +
      "; " + u.ratioReason).mkString(". ")
  }
}
