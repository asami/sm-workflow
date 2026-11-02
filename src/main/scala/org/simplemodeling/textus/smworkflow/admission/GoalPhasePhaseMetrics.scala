package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.resource.{ResourceTreeEntry, ResourceTreeQueryLimits, ResourceTreeSelection}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.workspace.{FileReadSnapshotResult, FileTarget, WorkspaceId}
import org.goldenport.record.Record

/** Passive endpoint observations in the owning native records. Metrics never
  * select work, authorize an effect, acquire usage or change a close outcome.
  * @since   Oct. 29, 2026
  * @version Nov.  2, 2026
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
  final case class Summary(schemaVersion: String, phaseSession: String, start: Option[Endpoint], end: Endpoint,
    netSourceLines: Option[Long], netReason: String, usage: Vector[Usage], profile: Option[GoalPhaseOperatingProfiles.Selection],
    issuedProfiles: Vector[GoalPhaseOperatingProfiles.Capture] = Vector.empty)

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
  given Encoder[Summary] = deriveEncoder
  given Decoder[Summary] = deriveDecoder

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
    summarize(session.current.identity.value, started.sourceMetrics, capture(core, selected, source), Vector.empty, started.operatingProfile)
      .copy(issuedProfiles = profiles.distinct)
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

  def record(summary: Summary): Record = GoalPhaseNativeMetadata.record(summary.asJson)
  def withMetrics(result: org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult,
    metrics: Option[Summary]): org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult =
    result.copy(completion = result.completion.map(value => value.copy(metrics = metrics.map(record))),
      message = metrics.map(value => result.message + " " + display(value)).getOrElse(result.message))

  def display(value: Summary): String = {
    def number(value: Option[Long]): String = value.map(_.toString).getOrElse("N/A")
    val source = "Source physical lines: start=" + number(value.start.flatMap(_.lines)) +
      "; end=" + number(value.end.lines) + "; net=" + number(value.netSourceLines) + ". "
    source + "Issued profiles=" + (if (value.issuedProfiles.isEmpty) "unavailable" else
      value.issuedProfiles.map(p => p.selection.identity + "@" + p.selection.revision).distinct.mkString(",")) + ". " + value.usage.map(u => u.destination + " tokens=" + number(u.totalTokens) + " (" + u.coverage +
      "); tokens/net source line=" + u.tokensPerNetSourceLine.map(_.bigDecimal.toPlainString).getOrElse("N/A") +
      "; " + u.ratioReason).mkString(". ")
  }
}
