package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import io.circe.Json
import org.goldenport.cncf.resource.{ResourceTreeEntry, ResourceTreeSelection}
import org.goldenport.cncf.workspace.{FileReadSnapshotResult, FileSnapshotLimits, FileTarget, WorkspaceId}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.caller.{NativeCallerPhaseMetrics as Caller}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhasePhaseMetrics as M
import M.given
import SplitPhaseExplicitPartitionFixture.take

/** Real managed current-workspace acquisition plus retained-native completion
  * and passive caller-resource fixtures. Numeric fixtures are not live usage.
  * @since   Oct. 29, 2026
  * @version Nov. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase610PhaseMetricsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _session = "metrics-spec-phase"
  private val _at = "2026-10-29T00:00:00Z"
  private val _source = GoalPhaseOrderedProgression.Snapshot("1" * 40, "2" * 40)
  private val _scope = M.Scope("sm-workflow-repository", "/spec/repository", Vector("src"),
    Vector("src/generated", "src/vendor"), Vector(".scala"))
  private def _endpoint(lines: Long, scope: M.Scope = _scope) = M.Endpoint(scope, "available", Some(lines), Some(1L),
    Some(lines), _at, _source.head, _source.tree, "SPECIFICATION-OWNED endpoint")
  private def _usage(id: String, destination: String = "codex", input: Long = 100, output: Long = 50) =
    M.UsageObservation(id, 1, _session, id, destination, "parent", Some("spec-model"), _at, _at,
      Some(input), Some(output), Some(25), Some(10), "self-only")

  "Caller route decisions" should {
    "bind selected and excluded decisions to one issued continuation and deduplicate snapshots" in {
      val state = io.circe.parser.parse("""{"schema":"sm-workflow.caller-dispatch.v1","status":"PREPARED",
        "request":{"work":{"kind":"ReviewStep"},"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"review:1"}},
        "policy":{"candidates":["ordinary","difficult"]},"attempts":[],"resolved":{"candidate":"ordinary"},
        "routeSelection":{"schema":"sm-workflow.caller-route-selection.v1","session":"metrics-spec-phase","continuation":"review:1",
        "kind":"ReviewStep","route":"codex","disposition":"selected","reason":"candidate-available"}}""").toOption.get
      val selected = Caller.retainedRoute(_session, "snapshot", 1L, state).get
      val rows = M.withRouteRows(Vector.empty, Vector(selected, selected))
      val review = rows.find(_.identity == "ai:codex:review").get
      review.total shouldBe Some(1)
      review.selected shouldBe Some(1)
      review.excluded shouldBe Some(0)
      review.coverage shouldBe "partial"
      Caller.retainedRoute("other", "snapshot", 1L, state) shouldBe None
      val crossed = state.mapObject(_.add("routeSelection", state.hcursor.downField("routeSelection").focus.get
        .mapObject(_.add("continuation", Json.fromString("wrong")))))
      Caller.retainedRoute(_session, "snapshot", 1L, crossed) shouldBe None
      val contradictory = selected.copy(disposition = "unknown", reason = "capability-unknown", source = "conflict")
      val unknown = M.withRouteRows(Vector.empty, Vector(selected, contradictory)).find(_.identity == "ai:codex:review").get
      unknown.selected shouldBe None
      unknown.unknown shouldBe Some(1)
    }
  }

  "Route alternatives and invocation outcomes" should {
    "retain distinct routes across revisions and reject same-route conflicts" in {
      val base = M.RouteFact(_session, "review:1", "api", 1, "one", "ReviewStep",
        "openai-daily", "excluded", "unavailable")
      val other = base.copy(revision = 2, source = "two", route = "openai-promotional-credit",
        disposition = "selected", reason = "verified-promotional-reservation")
      val rows = M.withRouteRows(Vector.empty, Vector(base, base, other))
      rows.find(_.identity == "ai:openai-daily:review").get.excluded shouldBe Some(1)
      rows.find(_.identity == "ai:openai-daily:review").get.reason should include ("unavailable=1")
      rows.find(_.identity == "ai:openai-promotional-credit:review").get.selected shouldBe Some(1)
      val conflict = base.copy(source = "conflict", disposition = "selected", reason = "other")
      val ambiguous = M.withRouteRows(Vector.empty, Vector(base, conflict, other))
      ambiguous.find(_.identity == "ai:openai-daily:review").get.unknown shouldBe Some(1)
      ambiguous.find(_.identity == "ai:openai-promotional-credit:review").get.selected shouldBe Some(1)
    }

    "count terminal calls once and preserve missing outcomes and nonstarts" in {
      val base = M.CallFact("one", 1, "one", "confirmed", None, Some("openai-daily"), None, None,
        None, None, None, None, None, None, None, None, outcome = Some("COMPLETED"))
      val failed = base.copy(invocation = "two", outcome = Some("FAILED"))
      val pending = base.copy(invocation = "three", outcome = None)
      val skipped = base.copy(invocation = "four", execution = "not-run", outcome = Some("BLOCKED"))
      val row = M.withCallRows(Vector.empty, Some(M.calls(Vector(base, base, failed, pending, skipped))))
        .find(_.identity == "ai:openai-daily:invocations").get
      row.started shouldBe Some(3)
      row.completed shouldBe Some(1)
      row.failed shouldBe Some(1)
      row.skipped shouldBe Some(1)
      row.unknown shouldBe Some(1)
      val pendingOnly = M.withCallRows(Vector.empty, Some(M.calls(Vector(pending))))
        .find(_.identity == "ai:openai-daily:invocations").get
      pendingOnly.completed shouldBe None
      pendingOnly.failed shouldBe None
    }

    "reconcile repeated revisions and leave conflicting terminal outcomes unknown" in {
      val started = M.CallFact("same", 1, "start", "confirmed", None, Some("openai-daily"), None, None,
        None, None, None, None, None, None, None, None)
      val done = started.copy(revision = 2, source = "done", outcome = Some("COMPLETED"))
      val replayed = M.withCallRows(Vector.empty, Some(M.calls(Vector(started, done, done))))
        .find(_.identity == "ai:openai-daily:invocations").get
      replayed.started shouldBe Some(1)
      replayed.completed shouldBe Some(1)
      replayed.unknown shouldBe Some(0)
      val contradiction = done.copy(source = "conflict", outcome = Some("FAILED"))
      val conflicted = M.withCallRows(Vector.empty, Some(M.calls(Vector(started, done, contradiction))))
        .find(_.identity == "ai:openai-daily:invocations").get
      conflicted.completed shouldBe None
      conflicted.failed shouldBe None
      conflicted.unknown shouldBe Some(1)
    }

    "separate a returned repair decision from call failure" in {
      val state = io.circe.parser.parse("""{"schema":"sm-workflow.caller-dispatch.v1","status":"RETURNED",
        "request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work"}},
        "launch":{"task_name":"agent"},"launchReturn":{"agent_id":"agent"},
        "semanticResult":{"outcome":"repair"}}""").toOption.get
      Caller.retainedCall(_session, "snapshot", 1, state, None, None).get.outcome shouldBe Some("COMPLETED")
    }
  }

  "Retained tool preview counts" should {
    "separate unchanged previews from accepted edits and leave absent routes unknown" in {
      val preview = GoalPhaseNativeScalaCleanup.PreviewObservation("preview:one", "scalafix", "unchanged")
      val rows = M.selectionRows(Vector.empty, Some(M.repairs(Vector.empty, Set.empty)), Vector(preview, preview))
      val selection = rows.find(_.identity == "repair:scalafix:preview").get
      selection.selected shouldBe Some(1)
      selection.completed shouldBe Some(1)
      selection.total shouldBe None
      rows.find(_.identity == "repair:scalafix").get.completed shouldBe None
      rows.find(_.identity == "repair:metals:preview").get.selected shouldBe None
      val pass = M.RepairZeroPass("pass:one", "scalafix", "step-1", "7", "index",
        Vector("src/Example.scala"), "candidate", "validation", "commit")
      val scoped = M.selectionRows(Vector.empty, Some(M.repairs(Vector.empty, Set.empty).copy(zeroPasses = Some(Vector(pass, pass)))))
      scoped.find(_.identity == "repair:scalafix").get.completed shouldBe None
      val zero = scoped.find(_.identity == "repair:scalafix:pass:pass:one").get
      zero.completed shouldBe Some(0)
      zero.coverage shouldBe "complete"
      zero.reason should include ("src/Example.scala")
      val diagnostics = Vector(
        M.DiagnosticObservation("d1", "metals", Some("no-action"), Some(true), Some(true)),
        M.DiagnosticObservation("d2", "metals", Some("disabled"), Some(false), None),
        M.DiagnosticObservation("d3", "metals", None, None, None))
      val observed = M.selectionRows(Vector.empty, None, Vector.empty, diagnostics)
        .find(_.identity == "repair:metals:diagnostic").get
      observed.total shouldBe Some(3)
      observed.selected shouldBe Some(1)
      observed.excluded shouldBe Some(1)
      observed.unknown shouldBe Some(1)
      observed.reason should include ("no-action=1")
    }
  }

  "Native validation selection counts" should {
    "project retained nonstarts without hiding later terminal or unobserved checks" in {
      val checks = Vector(
        GoalPhaseNativeValidation.Check("stopped", 1, WorkflowSbtValidation.Kind.Compile),
        GoalPhaseNativeValidation.Check("later", 2, WorkflowSbtValidation.Kind.Compile),
        GoalPhaseNativeValidation.Check("missing", 3, WorkflowSbtValidation.Kind.Compile))
      import org.goldenport.cncf.processexecution.*
      val target = SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1")))
      val empty = ProcessExecutionCapture(Vector.empty, 0L, false)
      def receipt(id: String, termination: ProcessExecutionTermination, elapsed: Long) = {
        val result = SbtExecutionResult(target, SbtOperation.Compile,
          ProcessExecutionResult(termination, empty, empty, Vector.empty, elapsed, "selected-sbt"), None)
        take(RepositorySyncNativeMergeValidation.sbtReceiptC(id, WorkflowSbtValidation.Kind.Compile, result))
      }
      val stopped = receipt("stopped", ProcessExecutionTermination.NotStarted, 0L)
      val previous = receipt("later", ProcessExecutionTermination.NotStarted, 0L)
      val completed = receipt("later", ProcessExecutionTermination.Exited(0), 7L)
      val payload = GoalPhaseNativeValidation.Payload("1", "S1", _source, checks,
        Vector(completed), None, stopped = Vector(stopped, previous))
      val observed = GoalPhaseNativeWorkContinuation.validationObservations(payload, "validation")
      val byId = observed.map(x => x.identity -> x).toMap
      byId("validation:stopped").outcome shouldBe "not-executed"
      byId("validation:stopped").reason shouldBe Some("not-started")
      byId("validation:stopped").started shouldBe Some(false)
      byId("validation:later").started shouldBe Some(true)
      byId("validation:later").outcome shouldBe "succeeded"
      byId("validation:later").reason shouldBe None
      byId("validation:missing").outcome shouldBe "unknown"
      byId("validation:missing").reason shouldBe None
      val counts = M.validationCounts(observed).find(_.identity == "validation:compile").get
      counts.skipped shouldBe Some(1)
      counts.unknown shouldBe Some(1)
      counts.reason should include ("not-started=1")
    }

    "deduplicate replay and preserve incomplete case and check evidence" in {
      Given("one successful Admission, one missing check result and a partial case inventory")
      val first = M.ValidationObservation("run:admission", "admission", Some(true), "succeeded", None,
        Some(5), Some(2), Some("partial"))
      val missing = M.ValidationObservation("run:compile", "compile", None, "unknown", None, None, None, None)
      When("the retained replay is projected")
      val rows = M.validationCounts(Vector(first, first, missing))
      val checks = rows.find(_.identity == "validation:admission").get
      checks.selected shouldBe Some(1)
      checks.completed shouldBe Some(1)
      checks.total shouldBe None
      val cases = rows.find(_.identity == "validation:admission:cases").get
      cases.total shouldBe Some(5)
      cases.selected shouldBe Some(2)
      cases.excluded shouldBe Some(3)
      cases.coverage shouldBe "partial"
      val compile = rows.find(_.identity == "validation:compile").get
      compile.selected shouldBe Some(1)
      compile.started shouldBe None
      compile.completed shouldBe None
      compile.unknown shouldBe Some(1)
      rows.find(_.identity == "validation:full").get.selected shouldBe None
      val conflict = M.validationCounts(Vector(first, first.copy(outcome = "failed")))
      conflict.find(_.identity == "validation:admission").get.selected shouldBe None
    }
  }

  "Operational time" should {
    "retain overlapping execution durations and expose incomplete and conflicting observations" in {
      val second = "2026-10-29T00:00:01Z"
      val span = M.ObservedSpan("a", "ai", Some(_at), Some(second), Some("review"), "receipt")
      val parallel = span.copy(identity = "b")
      val missing = span.copy(identity = "c", end = None)
      val conflict = span.copy(identity = "d")
      val result = M.operationalTime(Some(_at), Some(second), Vector(span, span, parallel, missing,
        conflict, conflict.copy(end = Some(_at))))
      result.elapsedMillis shouldBe Some(1000L)
      val ai = result.totals.find(_.category == "ai").get
      ai.millis shouldBe Some(2000L)
      ai.incompleteSpans shouldBe 1
      ai.conflictingIdentities shouldBe Vector("d")
      ai.coverage shouldBe "partial"
      result.totals.find(_.category == "human-wait").get.millis shouldBe None
      M.operationalTime(None, Some(second), Vector.empty).elapsedMillis shouldBe None
    }

    "sum measured native build durations separately from approval intervals and wall time" in {
      val end = "2026-10-29T00:00:01Z"
      val build = M.ObservedSpan("compile-result", "build-test", None, None, Some("compile"), "native", Some(0L))
      val test = build.copy(identity = "test-result", role = Some("test"), processMillis = Some(1500L))
      val wait = M.ObservedSpan("human-7", "human-wait", Some(_at), Some(end), Some("judgment"), "native")
      val unobserved = wait.copy(identity = "human-8", end = None)
      val totals = M.operationalTime(Some(_at), Some(end), Vector(build, build, test, wait, unobserved))
      totals.elapsedMillis shouldBe Some(1000L)
      totals.totals.find(_.category == "build-test").get.millis shouldBe Some(1500L)
      totals.totals.find(_.category == "human-wait").get.millis shouldBe Some(1000L)
      totals.totals.find(_.category == "human-wait").get.incompleteSpans shouldBe 1
    }
  }

  "Operational repair and rework projection" should {
    "group retained deterministic edits per episode and distinguish fallback, unresolved and absent proof" in {
      import GoalPhaseWorkComparison as C
      val facts = C.NativeOperations()
        .stage("compile-initial", "one", "candidate-1", "compile")
        .stage("test-initial", "one", "candidate-1", "test")
        .failure("failed-test", "one", "validation", "test")
        .attempted("one", "apply-1", Some("effect-1"))
        .failure("repeat-test-failure", "one", "validation", "test")
        .attempted("one", "apply-2", Some("effect-2"))
        .stage("compile-repeat", "one", "candidate-2", "compile")
        .stage("test-repeat-1", "one", "candidate-2", "test")
        .stage("test-repeat-2", "one", "candidate-2", "test")
        .succeeded("one", "validation", Set("compile"), "wrong-check")
        .succeeded("one", "validation", Set("test"), "verified")
        .stage("review-initial", "one", "review-1", "review")
        .failure("failed-review", "one", "review", "ReviewStep")
        .attempted("one", "apply-3", Some("effect-3"))
        .ai("one", Some(true))
        .stage("review-repeat", "one", "review-2", "review")
        .succeeded("one", "review", Set("ReviewStep"), "accepted")
        .failure("still-open", "two", "validation", "test")
        .attempted("two", "apply-4", None)
        .failure("diagnostics-only", "three", "validation", "test")
        .failure("unknown-ai", "four", "validation", "test")
        .attempted("four", "apply-5", Some("effect-5"))
        .ai("four", None)
        .succeeded("four", "validation", Set("test"), "verified-2")
      val repairs = M.repairs(Vector("effect-1", "effect-2", "effect-3", "effect-5").map(id =>
        M.RepairApplication(id, Some("metals"), "fixture", Some(true), Some(true), Some(true),
          Some(false), Some("accepted-tree"), Some("validation"), Vector("receipt:" + id))), Set.empty)
      val route = C.RouteSwitch("switch-1", "RepairStep", "ordinary", "difficult", Some("retained failure trend"))
      val aggregate = C.operational(facts.copy(episodes = facts.episodes ++ facts.episodes,
        runs = facts.runs ++ facts.runs, routes = Vector(route, route)), repairs)
      aggregate.resolvedWithoutAi shouldBe 1
      aggregate.attemptedWithKnownDisposition shouldBe 3
      aggregate.unresolvedAttempted shouldBe 1
      aggregate.unobservable shouldBe 2
      aggregate.episodes.size shouldBe 5
      aggregate.episodes.find(_.identity == "failed-test").flatMap(_.resolution) shouldBe Some("verified")
      aggregate.rework.map(r => r.stage -> r.cycles).toMap shouldBe Map("compile" -> Some(1),
        "test" -> Some(1), "lint" -> None, "review" -> Some(1))
      aggregate.routeSwitches shouldBe Vector(route)
      C.operational(C.NativeOperations(), repairs).coverage shouldBe "unavailable"
    }

    "retain the repair association for review after validation resolves the failure" in {
      import GoalPhaseWorkComparison as C
      val facts = C.NativeOperations().stage("review-1", "step", "first", "review")
        .failure("failure", "step", "validation", "compile")
        .attempted("step", "apply", Some("effect"))
        .succeeded("step", "validation", Set("compile"), "passed")
        .stage("review-2", "step", "second", "review")
      facts.runs.last.rework shouldBe true
    }
  }

  "Deterministic application projection" should {
    "count one retained application per actual identity and preserve incomplete coverage" in {
      def fact(id: String, tool: Option[String], changed: Option[Boolean] = Some(true),
        checked: Option[Boolean] = Some(true), retained: Option[Boolean] = Some(true),
        rollback: Option[Boolean] = Some(false)) = M.RepairApplication(id, tool, "native-receipt",
          changed, checked, retained, rollback, Some("candidate"), Some("validation"), Vector("receipt:" + id))
      val good = fact("a", Some("metals"))
      val rows = Vector(good, good, fact("b", Some("scalafix")), fact("c", Some("scalafmt")),
        fact("d", Some("built-in")), fact("rollback", Some("metals"), rollback = Some(true)),
        fact("unknown", Some("metals"), checked = None), fact("preview", Some("scalafix"), changed = Some(false)))
      val result = M.repairs(rows, Set("scalafix", "scalafmt", "built-in"))
      result.tools.map(x => x.tool -> x.count).toMap shouldBe Map("scalafix" -> Some(1),
        "metals" -> Some(1), "scalafmt" -> Some(1), "built-in" -> Some(1))
      result.tools.find(_.tool == "metals").map(_.coverage) shouldBe Some("partial")
      result.observations.count(_.identity == "a") shouldBe 1
      M.repairs(Vector.empty, Set("metals")).tools.find(_.tool == "metals").flatMap(_.count) shouldBe Some(0)
      M.repairs(Vector.empty, Set.empty).tools.find(_.tool == "metals").flatMap(_.count) shouldBe None
      M.repairs(Vector(good, good.copy(tool = Some("scalafix"))), Set.empty).coverage shouldBe "unavailable"
    }
  }

  "Retained byte attribution" should {
    "only count independently proven final writes after reversals or intermediate states" in {
      import GoalPhaseNativeScalaCleanup.ByteChange
      val project = GoalPhaseNativeWorkContinuation.retainedRepairApplications _
      def change(path: String, before: String, after: String) = ByteChange(path, before, after)
      val a = "a" * 64
      val b = "b" * 64
      val c = "c" * 64
      val reverseThenChange = Vector(Vector(change("One.scala", a, b)), Vector(change("One.scala", b, a)),
        Vector(change("One.scala", a, c)))
      project(reverseThenChange, Map("One.scala" -> c)) shouldBe Set(2)
      project(reverseThenChange.take(2), Map("One.scala" -> a)) shouldBe Set.empty
      project(Vector(Vector(change("One.scala", a, b)), Vector(change("One.scala", b, c))), Map("One.scala" -> c)) shouldBe Set(1)
      project(Vector(Vector(change("One.scala", a, b))), Map("One.scala" -> b)) shouldBe Set(0)
      project(Vector(Vector(change("One.scala", a, b)), Vector(change("Two.scala", a, c))),
        Map("One.scala" -> b, "Two.scala" -> c)) shouldBe Set(0, 1)
      project(Vector(Vector(change("One.scala", a, b), change("Two.scala", a, c))),
        Map("One.scala" -> b, "Two.scala" -> c)) shouldBe Set(0)
      project(reverseThenChange, Map("One.scala" -> b)) shouldBe Set.empty
      project(Vector(Vector(change("One.scala", a, b)), Vector(change("One.scala", c, b))), Map("One.scala" -> b)) shouldBe Set.empty
      project(Vector(Vector(change("One.scala", a, b), change("Two.scala", a, c))), Map("One.scala" -> b)) shouldBe Set.empty

      val proven = project(reverseThenChange, Map("One.scala" -> c))
      val rows = reverseThenChange.indices.map { index =>
        val retained = Option.when(proven.contains(index))(true)
        M.RepairApplication("app-" + index, Some(Vector("metals", "scalafix", "scalafmt")(index)),
          "native-receipt", Some(true), Some(true), retained, retained.map(_ => false),
          Some("tree"), Some("check"), Vector("receipt-" + index))
      }.toVector
      val counts = M.repairs(rows, Set.empty).tools.map(x => x.tool -> x.count).toMap
      counts("scalafmt") shouldBe Some(1)
      counts("metals") should not be Some(1)
      counts("scalafix") should not be Some(1)
    }
  }

  "Deterministic application provenance" should {
    "group several source writes into one tool application and exclude cancelled or failed effects" in {
      val first = M.RepairApplication("inspection:scalafix", Some("scalafix"), "scalafix-imports",
        Some(true), Some(true), Some(true), Some(false), Some("tree"), Some("check"),
        Vector("intent-result:file-a", "intent-result:file-b", "accepted-commit"))
      val cancelled = first.copy(identity = "cancelled", retained = Some(false))
      val failed = first.copy(identity = "failed", revalidated = Some(false))
      val rollback = first.copy(identity = "rolled-back", rolledBack = Some(true))
      val rejected = first.copy(identity = "rejected", changed = Some(false))
      val result = M.repairs(Vector(first, first, cancelled, failed, rollback, rejected), Set("scalafix"))
      result.tools.find(_.tool == "scalafix").map(_.count) shouldBe Some(Some(1))
      result.observations.find(_.identity == first.identity).map(_.sources.size) shouldBe Some(3)
      val unknown = first.copy(identity = "unknown-tool", tool = None)
      M.repairs(Vector(first, unknown), Set("scalafix")).tools.find(_.tool == "scalafix")
        .map(_.coverage) shouldBe Some("partial")
    }
  }

  "Phase source endpoint metrics" should {
    "count physical blank/comment/unterminated lines directly from immutable selected bytes" in {
      M.physicalLines(Vector.empty) shouldBe 0L
      M.physicalLines("\n// comment\n\nlast".getBytes(UTF_8).toVector) shouldBe 4L
      M.physicalLines("one\r\ntwo\r\n".getBytes(UTF_8).toVector) shouldBe 2L
      val entry = take(ResourceTreeEntry.createC("src/A.scala", "a\nlast".getBytes(UTF_8).toVector))
      val snapshot = FileReadSnapshotResult(FileTarget(take(WorkspaceId.parseC(_scope.workspace))),
        ResourceTreeSelection(_scope.includes, _scope.excludes, _scope.suffixes), FileSnapshotLimits.default, Vector(entry))
      val endpoint = M.fromSnapshot(_scope, _at, _source, snapshot)
      endpoint.lines shouldBe Some(2L)
      endpoint.files shouldBe Some(1L)
      endpoint.limits shouldBe Some(snapshot.limits)
      M.fromSnapshot(_scope.copy(workspace = "foreign"), _at, _source, snapshot).status shouldBe "unavailable"
    }

    "use the same rule for real managed additions deletions renames and pruned generated files" in {
      val root = WorkflowFileRuntimeFixture.fresh()
      Files.createDirectories(root.resolve("src/generated"))
      Files.createDirectories(root.resolve("src/vendor"))
      Files.writeString(root.resolve("src/A.scala"), "// authored\n\nlast")
      Files.writeString(root.resolve("src/generated/Generated.scala"), "generated\ngenerated\n")
      Files.writeString(root.resolve("src/vendor/Vendor.scala"), "vendor\n")
      val selected = _scope.copy(root = root.toString)
      val first = WorkflowFileRuntimeFixture.withCore(root, workspace = _scope.workspace) { core => M.capture(core, selected, _source) }
      first.status shouldBe "available"
      first.lines shouldBe Some(3L)
      Files.move(root.resolve("src/A.scala"), root.resolve("src/Renamed.scala"))
      Files.writeString(root.resolve("src/New.scala"), "new\nfile\n")
      Files.writeString(root.resolve("src/ignored.txt"), "not selected\n")
      val second = WorkflowFileRuntimeFixture.withCore(root, workspace = _scope.workspace) { core => M.capture(core, selected, _source) }
      second.lines shouldBe Some(5L)
      second.files shouldBe Some(2L)
      Files.delete(root.resolve("src/Renamed.scala"))
      val third = WorkflowFileRuntimeFixture.withCore(root, workspace = _scope.workspace) { core => M.capture(core, selected, _source) }
      third.lines shouldBe Some(2L)
      M.summarize(_session, Some(first), third, Vector.empty, None).netSourceLines shouldBe Some(-1L)
      // The producer default admits ordinary large source inventories, retaining finite caps.
      FileSnapshotLimits.default.maxEntries should be >= 4096
    }

    "preserve unavailable original starts and scope mismatch without inventing a denominator" in {
      val originalMissing = M.summarize(_session, None, _endpoint(200), Vector(_usage("one")), None)
      originalMissing.start shouldBe None
      originalMissing.netSourceLines shouldBe None
      originalMissing.usage.head.tokensPerNetSourceLine shouldBe None
      originalMissing.netReason should include("Original Phase start")
      M.summarize(_session, Some(_endpoint(10)), _endpoint(20, _scope.copy(countingRule = "other")), Vector.empty, None)
        .netSourceLines shouldBe None
      for (end <- Vector(10L, 5L)) {
        val value = M.summarize(_session, Some(_endpoint(10)), _endpoint(end), Vector(_usage("one")), None)
        value.netSourceLines shouldBe Some(end - 10)
        value.usage.head.tokensPerNetSourceLine shouldBe None
      }
    }

    "turn denied acquisition and byte overflow into an unavailable endpoint without changing completion" in {
      val root = WorkflowFileRuntimeFixture.fresh()
      Files.createDirectories(root.resolve("src"))
      Files.writeString(root.resolve("src/A.scala"), "long source\n")
      val selected = _scope.copy(root = root.toString)
      val denied = WorkflowFileRuntimeFixture.withCore(root, workspace = _scope.workspace, access = None) { core => M.capture(core, selected, _source) }
      denied.lines shouldBe None
      denied.status shouldBe "unavailable"
      val overflow = WorkflowFileRuntimeFixture.withCore(root, workspace = _scope.workspace, cap = 4) { core => M.capture(core, selected, _source) }
      overflow.status shouldBe "unavailable"
      overflow.lines shouldBe None
      M.summarize(_session, Some(denied), overflow, Vector.empty, None).usage.foreach(_.totalTokens shouldBe None)
    }

    "deduplicate receipts and inclusive parent coverage while retaining raw separate Codex local and partial ratios" in {
      val child = _usage("child", input = 60, output = 40)
      val parent = _usage("parent", input = 200, output = 100).copy(inclusion = "inclusive", coveredExecutions = Vector("child"))
      val local = _usage("local", destination = "local", input = 30, output = 10)
      val ignored = _usage("foreign").copy(phaseSession = "another-phase")
      val summary = M.summarize(_session, Some(_endpoint(10)), _endpoint(20), Vector(parent, child, parent, local, ignored), None)
      val codex = summary.usage.find(_.destination == "codex").get
      val localUsage = summary.usage.find(_.destination == "local").get
      codex.totalTokens shouldBe Some(300L)
      codex.cachedInputTokens shouldBe Some(25L)
      codex.reasoningOutputTokens shouldBe Some(10L)
      codex.tokensPerNetSourceLine shouldBe Some(BigDecimal(30))
      codex.coverage shouldBe "partial"
      codex.observations.map(_.execution) shouldBe Vector("parent")
      localUsage.totalTokens shouldBe Some(40L)
      localUsage.tokensPerNetSourceLine shouldBe Some(BigDecimal(4))
      M.aggregate(_session, Vector.empty, Some(10), "").foreach(_.totalTokens shouldBe None)
      val unconfirmed = M.aggregate(_session, Vector(parent.copy(inclusion = "unknown"), child), Some(10), "").head
      unconfirmed.totalTokens shouldBe Some(100L)
      unconfirmed.coverage shouldBe "partial"
      val inputOnly = M.aggregate(_session, Vector(child.copy(outputTokens = None)), Some(10), "").head
      inputOnly.inputTokens shouldBe Some(60L)
      inputOnly.totalTokens shouldBe None
    }

    "omit overlapping inclusive totals without inventing a union or a positive-net-line ratio" in {
      val child = _usage("shared-child", input = 60, output = 40).copy(coverage = "complete")
      val first = _usage("first-parent", input = 200, output = 100).copy(inclusion = "inclusive",
        coveredExecutions = Vector(child.execution), coverage = "complete")
      val second = _usage("second-parent", input = 300, output = 100).copy(inclusion = "inclusive",
        coveredExecutions = Vector(child.execution), coverage = "complete")
      // The explicit overlap is meaningful even when the child's receipt is absent.
      for (observations <- Vector(Vector(first, second, child), Vector(first, second), Vector(second, child, first))) {
        val summary = M.summarize(_session, Some(_endpoint(10)), _endpoint(20), observations, None)
        val codex = summary.usage.find(_.destination == "codex").get
        codex.coverage shouldBe "unavailable"
        codex.totalTokens shouldBe None
        codex.inputTokens shouldBe None
        codex.outputTokens shouldBe None
        codex.cachedInputTokens shouldBe None
        codex.reasoningOutputTokens shouldBe None
        codex.tokensPerNetSourceLine shouldBe None
        codex.ratioReason should include("unavailable")
        codex.reason should include("Overlapping inclusive observations were omitted")
        codex.observations shouldBe empty
      }
    }

    "retain unrelated confirmed inclusive and self-only usage when overlapping parents are omitted" in {
      val child = _usage("shared-child", input = 60, output = 40).copy(coverage = "complete")
      val first = _usage("first-parent", input = 200, output = 100).copy(inclusion = "inclusive",
        coveredExecutions = Vector(child.execution), coverage = "complete")
      val second = _usage("second-parent", input = 300, output = 100).copy(inclusion = "inclusive",
        coveredExecutions = Vector(child.execution), coverage = "complete")
      val independent = _usage("independent", input = 20, output = 10).copy(coverage = "complete")
      val separateChild = _usage("separate-child", input = 10, output = 5).copy(coverage = "complete")
      val separateParent = _usage("separate-parent", input = 50, output = 20).copy(inclusion = "inclusive",
        coveredExecutions = Vector(separateChild.execution), coverage = "complete")
      val local = _usage("local", destination = "local", input = 30, output = 10).copy(coverage = "complete")
      val confirmed = Vector(independent, separateParent, separateChild, local)
      val unaffected = M.aggregate(_session, confirmed, Some(10), "")
      unaffected.head.totalTokens shouldBe Some(100L)
      unaffected.head.coverage shouldBe "complete"
      val actual = M.aggregate(_session, Vector(first, second, child) ++ confirmed, Some(10), "")
      val codex = actual.head
      codex.totalTokens shouldBe Some(100L)
      codex.inputTokens shouldBe Some(70L)
      codex.outputTokens shouldBe Some(30L)
      codex.cachedInputTokens shouldBe Some(50L)
      codex.reasoningOutputTokens shouldBe Some(20L)
      codex.observations.map(_.execution).toSet shouldBe Set(independent.execution, separateParent.execution)
      codex.coverage shouldBe "partial"
      codex.tokensPerNetSourceLine shouldBe Some(BigDecimal(10))
      codex.ratioReason shouldBe "Partial observed usage per net source line"
      actual.find(_.destination == "local") shouldBe unaffected.find(_.destination == "local")
    }

    "consume real caller-harness resource fields without fetching or mistaking one call for Phase coverage" in {
      import WorkflowExecutionHarness as H
      val profile = WorkflowConcreteExecutionProfile("fixture", "fixture-provider", "fixture-model", Map.empty)
      val origin = WorkflowExecutorDescriptor("fixture-origin", profile)
      val mapping = take(WorkflowExecutionProfileMapping.createC("fixture", "1", Vector(profile),
        Vector(WorkflowExecutionProfileMapping.ClassBinding("Routine", profile.identity))))
      val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("fixture"),
        WorkflowDefinitionRevision("1"), InstanceIdentity(_session))
      val reference = H.WorkReference(handle, StateMachineRunIdentity("run"),
        org.goldenport.cncf.workflow.ContinuationIdentity("fixture-work"), StateMachineRevision("2"))
      val requirement = ExecutionRequirement(Vector(CapabilityRequirement("fixture")), RiskLevel("bounded"),
        ReasoningLevel.Standard, reviewRequired = false, Some(ExecutionDisposition(ExecutionPlacement.Inline,
          ExecutionIndependence.Optional, origin.contextIdentity)), Some(ReasoningClassReference("Routine")))
      val prepared = take(H.prepareIssuedC(reference, requirement, origin, mapping))
      val completed = take(prepared.recordC(prepared.nextSelection.get, H.Outcome(H.Status.Completed, H.Reason.Completed,
        "SPECIFICATION-OWNED resource observation", Some(origin), H.Resources(inputTokens = Some(123), outputTokens = Some(45)))))
      val restored = take(H.decodeC(take(H.encodeC(completed))))
      val observations = Caller.observations(_session, Vector(Caller.RetainedExecution(restored, "codex", "parent", _at, _at, "self-only")))
      val actual = M.aggregate(_session, observations ++ observations, Some(10), "").head
      actual.totalTokens shouldBe Some(168L)
      actual.coverage shouldBe "partial"
      actual.observations.head.model shouldBe Some("fixture-model")
      Caller.observations("foreign", Vector(Caller.RetainedExecution(restored, "codex", "parent", _at, _at))) shouldBe empty
    }

    "project mixed retained calls once while preserving partial usage and unknown execution" in {
      Given("two observed engines, a replay, and an unconfirmed dispatch")
      val first = M.CallFact("one", 1, "fixture:one", "confirmed", Some("model-a"), Some("included"),
        Some("implementation"), Some("high"), Some(false), Some(false), Some(0), Some(4), None,
        Some("provider"), Some("provider"), None, usageContract = Some("input-plus-output"))
      val second = first.copy(invocation = "two", source = "fixture:two", engine = Some("model-b"),
        route = Some("api"), input = Some(3), output = None, total = Some(9), totalSource = Some("provider"),
        retry = Some(true))
      val unknown = first.copy(invocation = "three", source = "fixture:three", execution = "unknown")
      val skipped = first.copy(invocation = "four", source = "fixture:four", execution = "not-run")
      When("the same result and a later revision are projected")
      val result = M.calls(Vector(first, first, second, second.copy(revision = 2), unknown, skipped))
      Then("each actual invocation contributes once and coverage is explicit")
      result.session.executions shouldBe 2
      result.session.input.value shouldBe Some(3L)
      result.session.input.observed shouldBe 2
      result.session.output.value shouldBe Some(4L)
      result.session.output.observed shouldBe 1
      result.session.total.value shouldBe Some(13L)
      result.session.retries shouldBe 1
      result.unknownExecutions shouldBe 1
      result.notRun shouldBe 1
      result.groups.map(_.engine).toSet shouldBe Set(Some("model-a"), Some("model-b"))
      result.observations.find(_.invocation == "one").get.input shouldBe Some(0L)
    }

    "read actual dispatch and bounded API facts without inferring a route" in {
      Given("retained successful starts and provider metadata")
      val dispatch = io.circe.parser.parse("""{"schema":"sm-workflow.caller-dispatch.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work-one"}},"status":"RETURNED","launch":{"task_name":"actual-one"},"launchReturn":{"agent_id":"actual-one"},"observed":{"model":"engine-a","effort":"high"},"resources":{"input_tokens":0,"output_tokens":4}}""").toOption.get
      val api = io.circe.parser.parse("""{"schema":"sm-workflow.api-review-attempt.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work-two"}},"arguments":{"input":{"attemptIdentity":"try-two"}},"status":"RETURNED","invocationIdentity":"actual-two","response":{"status":"COMPLETED","attemptIdentity":"try-two","continuationId":"work-two","metadata":{"ai.execution.provider":"openai","ai.execution.model":"engine-b","ai.usage.total_tokens":"9","ai.usage.total_tokens_source":"provider"}}}""").toOption.get
      When("retained facts are projected")
      val facts = Vector(Caller.retainedCall(_session, "dispatch.json", 1, dispatch, Some("subscription"), Some("review")),
        Caller.retainedCall(_session, "api.json", 1, api, None, Some("review"))).flatten
      Then("the actual identities and provenance survive and missing values remain unknown")
      facts.map(_.invocation) shouldBe Vector("work-one:actual-one", "work-two:actual-two")
      facts(0).engine shouldBe Some("codex")
      facts(0).dispatchKind shouldBe Some("codex")
      facts(0).model shouldBe Some("engine-a")
      facts(1).engine shouldBe Some("openai")
      facts(0).input shouldBe Some(0L)
      facts(0).totalSource shouldBe None
      facts(1).route shouldBe None
      facts(1).totalSource shouldBe Some("provider")
      val rows = M.withCallRows(Vector.empty, Some(M.calls(facts)))
      rows.find(_.identity == "ai:codex:invocations").get.started shouldBe Some(1)
      rows.find(_.identity == "ai:openai-daily:invocations").get.started shouldBe None
      M.calls(facts).session.total.value shouldBe Some(9L)
    }

    "count separately confirmed retry and fallback identities even after replay" in {
      Given("two independently confirmed invocation identities and one skipped candidate")
      val one = M.CallFact("actual-first", 1, "first", "confirmed", Some("engine"), Some("route"),
        Some("review"), Some("high"), Some(false), Some(false), Some(2L), Some(1L), None, None, None, None)
      val two = one.copy(invocation = "actual-second", source = "second", retry = Some(true), fallback = Some(true))
      val skipped = one.copy(invocation = "candidate-unstarted", source = "candidate", execution = "not-run")
      When("the observations are replayed")
      val result = M.calls(Vector(one, two, skipped, one, two))
      Then("the two calls and their known labels occur once each")
      result.session.executions shouldBe 2
      result.session.retries shouldBe 1
      result.session.fallbacks shouldBe 1
      result.session.total.value shouldBe None
      result.notRun shouldBe 1
    }

    "exclude conflicting facts and report token overflow without losing zero" in {
      Given("contradictory replay and independent large observations")
      val base = M.CallFact("conflict", 1, "one", "confirmed", Some("engine"), None, None, None,
        None, None, Some(1L), Some(0L), None, None, None, None)
      val huge = base.copy(invocation = "huge", input = Some(Long.MaxValue), output = Some(1L), usageContract = Some("input-plus-output"))
      val zero = base.copy(invocation = "zero", input = Some(0L), output = Some(0L), usageContract = Some("input-plus-output"))
      When("they are aggregated")
      val result = M.calls(Vector(base, base.copy(input = Some(2L)), huge, zero))
      Then("the contradiction is visible and overflowing totals are not wrapped")
      result.conflictingInvocations shouldBe Vector("conflict")
      result.session.executions shouldBe 3
      result.session.input.observed shouldBe 2
      result.session.total.overflow shouldBe true
      result.session.output.value shouldBe Some(1L)
    }

    "distinguish execution engine from model and configured entitlement route" in {
      Given("a Codex launch and two completed OpenAI executions using distinct routes")
      val codex = M.CallFact("c", 1, "c.json", "confirmed", Some("codex"), Some("subscription"),
        Some("review"), Some("high"), None, None, None, None, None, None, None, None, model = Some("shared-model"))
      val daily = codex.copy(invocation = "d", source = "d.json", engine = Some("openai"), route = Some("daily"))
      val monthly = daily.copy(invocation = "m", source = "m.json", route = Some("monthly"))
      When("their facts are grouped")
      val result = M.calls(Vector(codex, daily, monthly))
      Then("the same model does not collapse the three execution groups")
      result.groups.map(g => (g.engine, g.route)).toSet shouldBe Set(
        (Some("codex"), Some("subscription")), (Some("openai"), Some("daily")), (Some("openai"), Some("monthly")))
      result.observations.map(_.model).distinct shouldBe Vector(Some("shared-model"))
    }

    "distinguish a local API start from actual provider execution" in {
      Given("a bound local invocation with a blocked, uncertain, and completed response")
      def state_(status: String, response: String) = io.circe.parser.parse(
        """{"schema":"sm-workflow.api-review-attempt.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work"}},"arguments":{"input":{"attemptIdentity":"try"}},"status":"$$status","invocationIdentity":"local-id","response":$$response}""".replace("$$status", status).replace("$$response", response)).toOption.get
      val blocked = state_("BLOCKED", """{"status":"BLOCKED","attemptIdentity":"try","continuationId":"work"}""")
      val unavailable = state_("RETURNED", """{"status":"UNAVAILABLE","attemptIdentity":"try","continuationId":"work"}""")
      val declined = state_("RETURNED", """{"status":"DECLINED","attemptIdentity":"try","continuationId":"work"}""")
      val uncertain = state_("UNCERTAIN", "null")
      val failed = state_("RETURNED", """{"status":"FAILED","attemptIdentity":"try","continuationId":"work","metadata":{"ai.execution.provider":"openai","providerHttp":{"status":429}}}""")
      val completed = state_("RETURNED", """{"status":"COMPLETED","attemptIdentity":"try","continuationId":"work","metadata":{"ai.execution.provider":"openai","ai.execution.model":"model-x"}}""")
      When("each retained state is parsed")
      val facts = Vector(blocked, unavailable, declined, uncertain, failed, completed).zipWithIndex.flatMap { case (state, index) =>
        Caller.retainedCall(_session, "source-" + index, 1, state, Some("daily"), Some("review"))
      }
      Then("only the attributable completed provider call is confirmed")
      facts.map(_.execution) shouldBe Vector("not-run", "not-run", "not-run", "unknown", "confirmed", "confirmed")
      facts.last.engine shouldBe Some("openai")
      facts.last.model shouldBe Some("model-x")
      facts.head.outcome shouldBe Some("BLOCKED")
      facts.head.nonstartReason shouldBe Some("budget-refused")
      facts(2).nonstartReason shouldBe Some("declined")
      val distinct = facts.zipWithIndex.map { case (fact, index) => fact.copy(invocation = "case-" + index) }
      val counts = M.withCallRows(Vector.empty, Some(M.calls(distinct)))
      val daily = counts.find(_.identity == "ai:openai-daily:invocations").get
      daily.started shouldBe Some(2)
      daily.skipped shouldBe Some(3)
      daily.unknown shouldBe Some(1)
      daily.reason should include ("declined=1")
    }

    "keep unstarted fallback candidates separate from a confirmed launch" in {
      Given("a retained dispatch with a rejected candidate and a later launch")
      val state = io.circe.parser.parse("""{"schema":"sm-workflow.caller-dispatch.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work"}},"status":"RETURNED","launch":{"task_name":"actual"},"launchReturn":{"agent_id":"actual"},"attempts":[{"status":"UNAVAILABLE","executionStarted":false}],"observed":{"model":"m"}}""").toOption.get
      When("the retained prefix and launch are read")
      val facts = Caller.retainedCalls(_session, "retained", 1, state, None, None)
      Then("the skipped candidate does not count as a call")
      M.calls(facts).notRun shouldBe 1
      M.calls(facts).session.executions shouldBe 1
      M.calls(facts).session.fallbacks shouldBe 1
    }

    "keep execution and raw conflicts across re-projection and select newer cumulative usage" in {
      Given("two same-revision token disagreements after an earlier confirmed call")
      val first = M.CallFact("call", 1, "first", "confirmed", Some("codex"), None, None, None,
        None, None, Some(1L), Some(2L), None, None, None, None)
      val left = first.copy(revision = 2, source = "left", execution = "unknown", input = Some(4L))
      val right = left.copy(source = "right", input = Some(5L))
      When("the retained observations are repeatedly projected")
      val initial = M.calls(Vector(first, left, right))
      val replay = M.calls(initial.observations)
      Then("the call remains confirmed and the conflicted field remains unavailable")
      initial.session.executions shouldBe 1
      initial.session.input.value shouldBe None
      replay shouldBe initial
      initial.conflictingInvocations shouldBe Vector("call")
      initial.conflicts.head.fields should contain("input")
      val resolved = M.calls(initial.observations :+ left.copy(revision = 3, source = "latest", input = Some(7L)))
      resolved.session.executions shouldBe 1
      resolved.session.input.value shouldBe Some(7L)
    }

    "reconcile dispatch snapshots only within a uniquely launched continuation" in {
      def dispatch(work: String, status: String, launch: String) = io.circe.parser.parse(
        s"""{"schema":"sm-workflow.caller-dispatch.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"$work"}},"status":"$status",$launch"attempts":[{"executionStarted":false}]}""").toOption.get
      val prepared = dispatch("a", "PREPARED", "")
      val started = dispatch("a", "STARTED", "\"launch\":{},\"launchReturn\":{\"agent_id\":\"one\"},")
      val other = dispatch("b", "PREPARED", "")
      val facts = Caller.retainedCalls(_session, "one", 1, prepared, None, None) ++
        Caller.retainedCalls(_session, "two", 2, started, None, None) ++
        Caller.retainedCalls(_session, "three", 1, prepared, None, None) ++
        Caller.retainedCalls(_session, "four", 1, other, None, None)
      val result = M.calls(facts)
      result.session.executions shouldBe 1
      result.unknownExecutions shouldBe 1
      result.notRun shouldBe 2
      M.calls(result.observations) shouldBe result
    }

    "reject mismatched responses and derive only coherent contracted totals" in {
      val good = io.circe.parser.parse("""{"schema":"sm-workflow.api-review-attempt.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work"}},"arguments":{"input":{"attemptIdentity":"try"}},"status":"RETURNED","invocationIdentity":"actual","response":{"status":"COMPLETED","attemptIdentity":"try","continuationId":"work","metadata":{"ai.execution.provider":"real","ai.usage.input_tokens":"0","ai.usage.output_tokens":"4","ai.usage.contract":"input-plus-output"}}}""").toOption.get
      val bad = good.deepMerge(io.circe.parser.parse("""{"response":{"attemptIdentity":"wrong","metadata":{"ai.execution.provider":"wrong","ai.usage.total_tokens":"999"}}}""").toOption.get)
      val first = Caller.retainedCall(_session, "good", 1, good, None, None).get
      val replacement = Caller.retainedCall(_session, "bad", 2, bad, None, None).get
      replacement.execution shouldBe "unknown"
      replacement.provider shouldBe None
      replacement.total shouldBe None
      val result = M.calls(Vector(first, replacement))
      result.session.total.value shouldBe Some(4L)
      result.groups.head.provider shouldBe Some("real")
      val noContract = first.copy(usageContract = None)
      M.calls(Vector(noContract)).session.total.value shouldBe None
      val split = Vector(first.copy(source = "a", input = Some(1), output = None),
        first.copy(source = "b", revision = 2, input = None, output = Some(2)))
      M.calls(split).session.total.value shouldBe None
    }

    "keep rejected slots separate and deduplicate the retained rejection prefix" in {
      def json(s: String) = io.circe.parser.parse(s).toOption.get
      val rejected = json("""{"schema":"sm-workflow.caller-dispatch.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work"}},"status":"STOPPED","resolved":{"candidate":"first"},"rejection":{"executionStarted":false,"kind":"unavailable"},"attempts":[]}""")
      val returned = json("""{"schema":"sm-workflow.caller-dispatch.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work"}},"status":"RETURNED","launch":{},"launchReturn":{"agent_id":"second"},"attempts":[{"candidate":"first","executionStarted":false,"reason":{"executionStarted":false,"kind":"unavailable"}}]}""")
      val facts = Caller.retainedCalls(_session, "first", 1, rejected, None, None) ++
        Caller.retainedCalls(_session, "replay", 2, returned, None, None)
      val result = M.calls(facts)
      result.notRun shouldBe 1
      result.session.executions shouldBe 1
      result.conflictingInvocations shouldBe empty
    }

    "join only the same API attempt and ignore crossed observed effort" in {
      def api(attempt: String, invocation: String, response: String, effort: String) = io.circe.parser.parse(
        s"""{"schema":"sm-workflow.api-review-attempt.v1","request":{"issued_work_context":{"run_id":"metrics-spec-phase","continuation_id":"work"}},"arguments":{"input":{"attemptIdentity":"$attempt"}},$invocation"observed":{"effort":"$effort"},"response":$response}""").toOption.get
      val prepared = api("first", "", "null", "wrong")
      val actual = api("first", "\"invocationIdentity\":\"actual-1\",", """{"attemptIdentity":"first","continuationId":"work","status":"COMPLETED"}""", "high")
      val crossed = api("first", "\"invocationIdentity\":\"actual-1\",", """{"attemptIdentity":"other","continuationId":"work","status":"COMPLETED"}""", "wrong")
      val second = api("second", "\"invocationIdentity\":\"actual-2\",", """{"attemptIdentity":"second","continuationId":"work","status":"COMPLETED"}""", "low")
      val facts = Vector(prepared, actual, crossed, second).zipWithIndex.map { case (state, i) =>
        Caller.retainedCall(_session, "s" + i, i.toLong, state, None, None).get
      }
      facts.head.level shouldBe None
      facts(2).level shouldBe None
      val result = M.calls(facts ++ facts)
      result.session.executions shouldBe 2
      result.unknownExecutions shouldBe 0
      result.groups.map(_.level).toSet shouldBe Set(Some("high"), Some("low"))
    }

    "select input and output values with their own retained provenance" in {
      val old = M.CallFact("pair", 1, "old", "confirmed", None, None, None, None, None, None,
        Some(3), Some(4), None, Some("provider-a"), Some("provider-a"), None)
      def selected(rows: Vector[M.CallFact], input: Boolean) = {
        val revisions = rows.groupBy(_.revision).toVector.sortBy(-_._1)
        if (input) M.selectUsagePair(revisions, _.input, _.inputSource)
        else M.selectUsagePair(revisions, _.output, _.outputSource)
      }
      Vector(true, false).foreach { input =>
        def change(f: M.CallFact, number: Option[Long], source: Option[String]) =
          if (input) f.copy(input = number, inputSource = source) else f.copy(output = number, outputSource = source)
        val olderValue = if (input) 3L else 4L
        val newer = change(old.copy(revision = 2, source = "new"), Some(7), None)
        selected(Vector(old, newer), input) shouldBe ((Some(7L), None, false, false))
        val sourceOnly = change(newer, None, Some("provider-b"))
        selected(Vector(old, sourceOnly), input) shouldBe ((Some(olderValue), Some("provider-a"), false, false))
        val agreeing = change(newer.copy(source = "other"), Some(7), Some("provider-b"))
        selected(Vector(old, newer, agreeing), input) shouldBe ((Some(7L), None, false, true))
        val disagreement = change(agreeing, Some(8), Some("provider-b"))
        selected(Vector(old, newer, disagreement), input) shouldBe ((None, None, true, false))
        val projected = M.calls(Vector(old, newer, agreeing))
        val coverage = if (input) projected.session.input else projected.session.output
        coverage.value shouldBe Some(7L)
        projected.conflicts.head.fields should contain(if (input) "inputSource" else "outputSource")
        val conflicted = M.calls(Vector(old, newer, disagreement))
        conflicted.conflicts.head.fields should contain(if (input) "input" else "output")
      }
      val legacy = io.circe.parser.parse("""{"invocation":"legacy","revision":1,"source":"old","execution":"confirmed","input":3,"output":4,"provenance":[],"conflictingFields":[]}""").toOption.get
        .as[M.CallFact].toOption.get
      val legacyResult = M.calls(Vector(legacy))
      legacyResult.session.input.value shouldBe Some(3L)
      legacyResult.session.output.value shouldBe Some(4L)
      selected(Vector(legacy), true)._2 shouldBe None
      selected(Vector(legacy), false)._2 shouldBe None
    }

    "retain the provenance of the winning total observation" in {
      val old = M.CallFact("one", 1, "old", "confirmed", None, None, None, None, None, None,
        Some(1), Some(2), None, None, None, None, usageContract = Some("input-plus-output"))
      val current = old.copy(revision = 2, source = "new", total = Some(5), totalSource = Some("provider"))
      val result = M.calls(Vector(old, current))
      result.session.total.value shouldBe Some(5L)
      result.conflictingInvocations shouldBe empty
    }

    "bind retained call facts to the actual work and preserve v1 absence" in {
      import io.circe.syntax.*
      import GoalPhaseRetainedObservations as R
      val plan = GoalPhaseOrderedProgression.Plan("metrics", "1", "6.10", "sm-workflow",
        Vector(GoalPhaseOrderedProgression.Step("one", "fixture", Vector("src"), Vector.empty, Vector("compile"))), Vector("test"))
      val continuation = _session + ":ordered-implementation:1"
      val invocation = continuation + ":agent"
      val fact = M.CallFact(invocation, 1, "receipt", "confirmed", Some("codex"), Some("route"),
        Some("implementation"), None, Some(false), Some(false), Some(0L), None, None, None, None, None,
        phaseSession = Some(_session), continuation = Some("dispatch:" + continuation), unresolvedDispatch = Some(false))
      val attempt = R.Attempt(continuation, Some("one"), "ImplementStep", invocation, "completed",
        None, None, "receipt", Vector.empty, Some(fact))
      val retained = R.Retained(R.schema, _session, "host", Vector(attempt), Vector.empty, Vector.empty)
      val native = Vector(GoalPhaseWorkComparison.NativeWork(Some("one"), "ImplementStep", None, Some(continuation)))
      R.bindC(retained, _session, plan, Vector.empty, native).isSuccess shouldBe true
      val decision = M.RouteFact(_session, continuation, "dispatch", 1, "receipt", "ImplementStep", "codex",
        "selected", "candidate-available", Some("one"))
      val routed = retained.copy(routes = Some(Vector(decision)))
      R.bindC(routed, _session, plan, Vector.empty, native).isSuccess shouldBe true
      R.bindC(routed.copy(routes = Some(Vector(decision.copy(stepIdentity = Some("other"))))),
        _session, plan, Vector.empty, native).isSuccess shouldBe false
      val counts = M.summarize(_session, None, _endpoint(20), Vector.empty, None).copy(
        retainedObservations = Some(routed), selectionCounts = Some(M.selectionRows(Vector.empty, None)))
      M.withComparison(counts, plan, Vector.empty, native).selectionCounts.get
        .find(_.identity == "ai:codex:implementation").get.selected shouldBe Some(1)
      val callerNative = native.map(_.copy(callerRouteObservation = Some(decision)))
      val callerCounts = counts.copy(retainedObservations = None)
      val callerProjection = M.withComparison(callerCounts, plan, Vector.empty, callerNative ++ callerNative)
      callerProjection.routeObservations shouldBe Some(Vector(decision))
      callerProjection.selectionCounts.get.find(_.identity == "ai:codex:implementation").get.selected shouldBe Some(1)
      val legacyResult = GoalPhaseNativeWork.Result("completed", "fixture", Vector.empty)
      val routedResult = legacyResult.copy(callerRouteObservation = Some(decision))
      GoalPhaseNativeWork.resultCodec.decode(GoalPhaseNativeWork.resultCodec.encode(legacyResult)) shouldBe Right(legacyResult)
      GoalPhaseNativeWork.resultCodec.decode(GoalPhaseNativeWork.resultCodec.encode(routedResult)) shouldBe Right(routedResult)
      GoalPhaseNativeWork.resultCodec.encode(routedResult).hcursor.get[String]("schemaVersion").toOption shouldBe
        Some("sm-workflow.goal-phase-native-work-result.v4")
      val summary = M.summarize(_session, None, _endpoint(20), Vector.empty, None).copy(retainedObservations = Some(retained))
      val projected = M.withComparison(summary, plan, Vector.empty, native)
      projected.calls.get.session.executions shouldBe 1
      projected.calls.get.session.input.value shouldBe Some(0L)
      projected.calls.get.session.output.value shouldBe None
      M.withComparison(projected, plan, Vector.empty, native).calls shouldBe projected.calls
      R.bindC(retained, _session, plan, Vector.empty, native.map(_.copy(kind = "ReviewStep"))).isSuccess shouldBe false
      R.valid(retained.copy(attempts = Vector(attempt.copy(call = Some(fact.copy(source = "other")))))) shouldBe false
      R.valid(retained.copy(attempts = Vector(attempt.copy(call = Some(fact.copy(phaseSession = Some("other"))))))) shouldBe false
      val legacy = retained.copy(attempts = Vector(attempt.copy(call = None)))
      val wire = legacy.asJson
      wire.hcursor.downField("attempts").downN(0).downField("call").focus shouldBe None
      R.decodeC(wire).toOption shouldBe Some(legacy)
    }

    "preserve legacy absent Start metrics through the actual native Start codec" in {
      val plan = GoalPhaseOrderedProgression.Plan("metrics-legacy", "1", "6.10", "sm-workflow",
        Vector(GoalPhaseOrderedProgression.Step("one", "Owned fixture", Vector("src"), Vector.empty, Vector("compile"))), Vector("test"))
      val legacy = GoalPhaseNativeRun.Payload("fixture-participant", _scope.root, "main", plan, _source)
      val wire = GoalPhaseNativeRun.codec.encode(legacy)
      wire.hcursor.downField("payload").downField("sourceMetrics").focus shouldBe None
      val restored = GoalPhaseNativeRun.codec.decode(wire).toOption.get
      restored.sourceMetrics shouldBe None
      val measured = legacy.copy(sourceMetrics = Some(_endpoint(100)))
      GoalPhaseNativeRun.codec.decode(GoalPhaseNativeRun.codec.encode(measured)).toOption.get shouldBe measured
    }

    "retain native Start and completion endpoints on reopen without resampling later unrelated source" in {
      object Complete extends GoalPhasePostCompletionSyncFixture
      val root = Complete.freshRoot("p610-endpoint-completion")
      var session = ""
      var terminal = Option.empty[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult]
      Complete.episode(root, initialize = true) { episode =>
        session = episode.closeDevelopment()._1
        val checks = Complete.phaseExecutions(root)
        val closed = episode.advance(session)
        closed.kind shouldBe "Terminal"
        closed.outcome shouldBe Some("completed")
        val metric = closed.completion.get.metrics.get
        val summary = io.circe.parser.parse(take(metric.toJsonStringC)).toOption.get
        summary.hcursor.downField("start").get[String]("status").toOption shouldBe Some("available")
        summary.hcursor.downField("end").get[String]("status").toOption shouldBe Some("available")
        val decoded = GoalPhaseNativeMetadata.normalizePublicJson(summary).toOption.get.as[M.Summary].toOption.get
        decoded.operationalTime.flatMap(_.elapsedMillis) should not be empty
        decoded.operations.toVector.flatMap(_.rework).find(_.stage == "review").flatMap(_.cycles) shouldBe Some(0)
        val repairs = M.repairs(Vector(M.RepairApplication("fixture-effect", Some("metals"), "fixture",
          Some(true), Some(true), Some(true), Some(false), Some("tree"), Some("check"), Vector("receipt"))), Set.empty)
        val completion = closed.completion.get.copy(metrics = Some(M.record(decoded.copy(deterministicRepairs = Some(repairs)))))
        val projected = Caller.project(session, completion, Vector.empty)
        val projectedJson = io.circe.parser.parse(take(projected.metrics.get.toJsonStringC)).toOption.get
        val reparsed = GoalPhaseNativeMetadata.normalizePublicJson(projectedJson).toOption.get.as[M.Summary].toOption.get
        reparsed.deterministicRepairs shouldBe Some(repairs)
        reparsed.operations shouldBe decoded.operations
        reparsed.operationalTime shouldBe decoded.operationalTime
        closed.message should include("Source physical lines")
        terminal = Some(closed)
        Complete.phaseExecutions(root) shouldBe checks
      }
      Files.createDirectories(root.resolve("src"))
      Files.writeString(root.resolve("src/Later.scala"), "unrelated\n" * 100)
      Complete.episode(root) { episode =>
        episode.advance(session) shouldBe terminal.get
        episode.advance(session) shouldBe terminal.get
      }
    }
  }
}
