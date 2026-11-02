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
  * @version Nov.  2, 2026
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
