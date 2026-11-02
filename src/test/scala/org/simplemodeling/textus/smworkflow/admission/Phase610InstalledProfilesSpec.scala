package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import io.circe.syntax.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.{WorkflowConcreteExecutionProfile, WorkflowExecutionProfileMapping}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** Changed installed/caller route plus actual native close/reopen. All executor,
  * Human and reviewer facts in this suite are specification-owned, never live
  * provider qualification or acceptance of the development Phase.
  * @since   Nov.  2, 2026
  * @version Nov.  2, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase610InstalledProfilesSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhaseWorkClassification.*
  private val _requirement = ExecutionRequirement(Vector(CapabilityRequirement("fixture")), RiskLevel("SOURCE_IMPLEMENTATION"),
    ReasoningLevel.Routine, false, reasoningClass = Some(ReasoningClassReference("CodingEngineeringRoutine")))
  private def _class(complexity: Complexity, nature: Option[String] = None) =
    Classification(Kind.CODING, complexity, Domain.ENGINEERING, Footprint.SMALL, nature)
  private def _capture(c: Classification, requirement: ExecutionRequirement = _requirement, isolated: Boolean = false) =
    take(GoalPhaseDispatchPolicy.captureC("ImplementStep", Some(c), requirement, isolated))

  "Forward logical dispatch and portable caller" should {
    "declare both passive host keys in the canonical component initialization snapshot" in {
      Given("the same production declaration vector used by ordinary configured components and native close fixtures")
      val declarations = ThreeWorkflowRuntimeParameters.declarations
      Then("each newly resolved optional property has exactly one registered typed declaration")
      declarations.count(_.name == GoalPhaseNativeExecution.logicalOrigin.name) shouldBe 1
      declarations.count(_.name == GoalPhaseRetainedObservations.declaration.name) shouldBe 1
      declarations.exists(_ == GoalPhaseNativeExecution.logicalOrigin) shouldBe true
      declarations.exists(_ == GoalPhaseRetainedObservations.declaration) shouldBe true
    }
    "capture the forward route at actual native issuance without rerouting pending legacy work" in {
      new Phase610OperatingProfilesSpec().qualifyForwardDispatch(this)
    }

    "distinguish explicit mechanical work from collapsed Routine and retain eligible parent INLINE" in {
      Given("Routine classifications whose nature is retained through the public classification codec")
      val mechanical = _class(Complexity.TRIVIAL, Some("mechanical"))
      mechanical.asJson.as[Classification].toOption.get shouldBe mechanical
      take(mechanical.requirementC(_requirement)).reasoningClass.get.value shouldBe "CodingEngineeringMechanical"
      _capture(mechanical).candidates shouldBe Vector("mechanical", "small", "sol-medium", "difficult", "hardest")
      _capture(_class(Complexity.SIMPLE)).role shouldBe "small"
      _capture(_class(Complexity.TRIVIAL)).role shouldBe "ordinary"
      _capture(_class(Complexity.STANDARD)).candidates shouldBe Vector("ordinary", "difficult", "hardest")
      _capture(_class(Complexity.COMPLEX), _requirement.copy(reasoning = ReasoningLevel.Deep)).role shouldBe "difficult"
      _capture(_class(Complexity.COMPLEX, Some("hardest"))).role shouldBe "hardest"
      _capture(_class(Complexity.SIMPLE, Some("parent-inline"))).placement shouldBe "INLINE"
      _capture(_class(Complexity.SIMPLE, Some("parent-inline")), isolated = true).placement shouldBe "DELEGATED"
      _capture(_class(Complexity.SIMPLE, Some("parent-inline")), _requirement.copy(reviewRequired = true)).placement shouldBe "DELEGATED"
      _capture(_class(Complexity.TRIVIAL, Some("deterministic"))).placement shouldBe "RUNTIME"
      GoalPhaseDispatchPolicy.policyValid(GoalPhaseDispatchPolicy.defaultPolicy.copy(edges = Map("ordinary" -> "ordinary"))) shouldBe false
      val customized = GoalPhaseDispatchPolicy.defaultPolicy.copy(edges = Map("ordinary" -> "difficult"))
      take(GoalPhaseDispatchPolicy.captureC("ImplementStep", Some(_class(Complexity.STANDARD)), _requirement, false, customized))
        .candidates shouldBe Vector("ordinary", "difficult")
      val legacy = GoalPhaseOperatingProfiles.Capture(GoalPhaseOperatingProfiles.Selection("standard", "1", "default"), None, None, None, None, None)
      legacy.asJson.hcursor.downField("dispatch").focus shouldBe None
      legacy.asJson.as[GoalPhaseOperatingProfiles.Capture].toOption.get shouldBe legacy
    }

    "execute the packaged adapter contract against exact tool arguments and retained returns" in {
      Given("the existing managed CPython caller environment, a fixture-owned tool callback, exposed capabilities and actual product role instructions")
      val generated = getClass.getClassLoader.getResource("META-INF/sm-workflow/install-sm-workflow.py")
      generated should not be null
      generated.getProtocol shouldBe "file"
      val builder = new ProcessBuilder("uv", "run", "--no-cache", "--managed-python", "--no-python-downloads",
        "--script", "scripts/tests/test_goal_phase_dispatch.py").redirectErrorStream(true)
      builder.environment().put("SM_WORKFLOW_GENERATED_RESOURCES", java.nio.file.Paths.get(generated.toURI).getParent.toString)
      val step = GoalPhaseOrderedProgression.Step("adapter", "Public adapter fixture", Vector("src"), Vector.empty, Vector.empty)
      val plan = GoalPhaseOrderedProgression.Plan("adapter", "1", "fixture", "fixture", Vector(step), Vector.empty)
      val capture = GoalPhaseOperatingProfiles.Capture(GoalPhaseOperatingProfiles.Selection("standard", "1", "default"),
        None, None, None, None, None, dispatch = Some(_capture(_class(Complexity.TRIVIAL, Some("mechanical")))))
      val request = GoalPhaseNativeWork.Request("fixture", "1", "ReviewStep", Some(step.identity), 1, step.purpose, plan,
        GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40), Vector.empty, None, None, None, operatingProfile = Some(capture))
      // Feed the installed adapter the production CLI json/data egress, including
      // its public property naming; do not hand-correct keys before preparation.
      builder.environment().put("SM_WORKFLOW_PUBLIC_DISPATCH_WORK",
        NativeExternalEvidenceToolFixture.normalCommandJson(NativeWorkflowOperationOutput.goalWork(request)))
      val process = builder.start()
      val output = new String(process.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
      val exit = process.waitFor()
      withClue(output) { exit shouldBe 0 }
    }

    "qualify role-specific opt-in cost mappings without importing the standard fallback chain" in {
      Given("an explicit cost mapping over existing reasoning classes")
      val profiles = Vector(
        WorkflowConcreteExecutionProfile("cost-luna", "codex", "gpt-6-luna", Map("effort" -> "medium")),
        WorkflowConcreteExecutionProfile("cost-terra", "codex", "gpt-6.1-terra", Map("effort" -> "medium")),
        WorkflowConcreteExecutionProfile("cost-sol", "codex", "gpt-6.1-sol", Map("effort" -> "high")))
      val mapping = take(WorkflowExecutionProfileMapping.createC("cost-role-candidates", "2026-10-30", profiles,
        Vector(WorkflowExecutionProfileMapping.ClassBinding("CodingEngineeringMechanical", "cost-luna"),
          WorkflowExecutionProfileMapping.ClassBinding("CodingEngineeringStandard", "cost-terra"),
          WorkflowExecutionProfileMapping.ClassBinding("ReviewStandard", "cost-terra"),
          WorkflowExecutionProfileMapping.ClassBinding("CodingEngineeringDeep", "cost-sol"))))
      take(mapping.resolveClassC(ReasoningClassReference("CodingEngineeringMechanical"))).model shouldBe "gpt-6-luna"
      take(mapping.resolveClassC(ReasoningClassReference("ReviewStandard"))).model shouldBe "gpt-6.1-terra"
      take(mapping.fallbackClassC(ReasoningClassReference("ReviewStandard"))) shouldBe None
      mapping.resolveClassC(ReasoningClassReference("Missing")).isSuccess shouldBe false
    }
  }

  "Ordinary-work comparison" should {
    "use a common terminal denominator including failed outcomes and preserve unknown historical facts" in {
      import GoalPhaseWorkComparison.*
      val start = "2026-11-02T00:00:00Z"
      val end = "2026-11-02T00:01:00Z"
      val cohort = Cohort("fixture", "Step", "coding", Some(start), end, "source@1")
      def row(id: String, outcome: String, first: Option[Boolean]) =
        Work(id, "Step", "coding", outcome, first, Some(start), Some(end), Vector("standard@1"), Vector(id))
      val rows = Vector(row("accepted", "accepted", Some(false)), row("failed", "failed", Some(false)),
        row("cancelled", "cancelled", Some(false)), row("deferred", "deferred", Some(false)),
        row("exhausted", "exhausted", Some(false)), row("abandoned", "abandoned", Some(false)),
        row("pending", "pending", None).copy(terminalAt = None), row("other", "accepted", Some(true)).copy(unit = "Task"))
      val summary = project("fixture", cohort, rows ++ rows, Vector.empty)
      summary.terminal shouldBe 6
      summary.eventualSuccess shouldBe 1
      summary.firstPassSuccess shouldBe 0
      summary.pending shouldBe 1
      summary.times.find(_.identity == "accepted").get.completionMillis shouldBe Some(60000L)
      summary.times.find(_.identity == "failed").get.completionMillis shouldBe None
      summary.times.find(_.identity == "pending").get.elapsedMillis shouldBe Some(60000L)
      summary.usage.foreach(_.totalTokens shouldBe None)
      project("fixture", cohort, Vector(row("unknown", "accepted", None).copy(startedAt = None)), Vector.empty).firstPassRate shouldBe None
    }

    "deduplicate attributable failed and repair attempts, retaining mixed profiles and separate destinations" in {
      import GoalPhaseWorkComparison.*
      val at = "2026-11-02T00:00:00Z"
      val cohort = Cohort("fixture", "Task", "coding", Some(at), at, "source@1")
      val work = Work("task", "Task", "coding", "accepted", Some(false), None, None,
        Vector("cost@1", "standard@1"), Vector("failed", "repair", "local"))
      def usage(id: String, destination: String) = GoalPhasePhaseMetrics.UsageObservation(id, 1, "fixture", id,
        destination, "worker", None, at, at, Some(10L), Some(20L), inclusion = "self-only", status = "failed")
      val resources = Vector(usage("failed", "codex"), usage("repair", "codex"), usage("local", "local"))
      val summary = project("fixture", cohort, Vector(work), resources ++ resources)
      summary.usage.find(_.destination == "codex").get.totalTokens shouldBe Some(60L)
      summary.usage.find(_.destination == "local").get.totalTokens shouldBe Some(30L)
      summary.mixed shouldBe Vector("task")
      summary.times.head.completionMillis shouldBe None
    }
  }

  "Retained caller facts" should {
    "bind closed bounded observations to actual native work and preserve missing historical facts" in {
      import GoalPhaseRetainedObservations.*
      import GoalPhaseWorkComparison.*
      val at = "2026-11-02T00:00:00Z"
      val end = "2026-11-02T00:01:00Z"
      val step = GoalPhaseOrderedProgression.Step("step", "fixture", Vector("src"), Vector.empty, Vector.empty)
      val plan = GoalPhaseOrderedProgression.Plan("fixture", "1", "fixture", "fixture", Vector(step), Vector.empty)
      val commit = GoalPhaseOrderedProgression.Commit("a" * 40, "b" * 40, "c" * 40)
      val closed = Vector(GoalPhaseOrderedProgression.ClosedStep("step", commit, Some("accepted-review")))
      val work = Vector(NativeWork(Some("step"), "ImplementStep", None, Some("session:implementation:1"), Some("completed")),
        NativeWork(Some("step"), "ReviewStep", None, Some("session:review:2"), Some("accept")))
      def attempt(n: NativeWork, id: String) = Attempt(n.continuationIdentity.get, n.stepIdentity, n.kind, id,
        "completed", Some(at), Some(end), "fixture:actual-return:" + id, Vector.empty)
      val facts = Retained(schema, "session", "fixture:retained-caller", Vector(attempt(work(0), "one"), attempt(work(1), "two")),
        Vector(Closure("step", commit.identity, end, "fixture:actual-local-commit")), Vector("step"))
      take(decodeC(facts.asJson)) shouldBe facts
      take(parseC(facts.asJson.noSpaces)) shouldBe facts
      parseC("{").isSuccess shouldBe false
      parseC("x" * 1048577).isSuccess shouldBe false
      parseC(facts.asJson.noSpaces.dropRight(1) + ",\"phaseSession\":\"session\"}").isSuccess shouldBe false
      decodeC(facts.asJson.mapObject(_.add("invented", io.circe.Json.True))).isSuccess shouldBe false
      decodeC(facts.asJson.mapObject(_.remove("sourceReference"))).isSuccess shouldBe false
      valid(facts.copy(attempts = facts.attempts :+ facts.attempts.head)) shouldBe false
      valid(facts.copy(attempts = Vector(facts.attempts.head.copy(endedAt = Some("2026-11-01T00:00:00Z"))))) shouldBe false
      take(bindC(facts, "session", plan, closed, work)) shouldBe facts
      bindC(facts, "another-session", plan, closed, work).isSuccess shouldBe false
      bindC(facts.copy(attempts = Vector(facts.attempts.head.copy(kind = "ReviewStep"))), "session", plan, closed, work).isSuccess shouldBe false
      bindC(facts.copy(attempts = Vector(facts.attempts.head.copy(stepIdentity = Some("crossed")))), "session", plan, closed, work).isSuccess shouldBe false
      bindC(facts.copy(closures = Vector(facts.closures.head.copy(commitIdentity = "d" * 40))), "session", plan, closed, work).isSuccess shouldBe false
      val fresh = native(plan, closed, work, Some(facts)).head
      fresh.firstPass shouldBe Some(true)
      fresh.startedAt shouldBe Some(at)
      fresh.terminalAt shouldBe Some(end)
      fresh.executions shouldBe Vector("one", "two")
      val history = native(plan, closed, work).head
      history.firstPass shouldBe None
      history.startedAt shouldBe None
      history.terminalAt shouldBe None
      history.executions shouldBe empty
      val repair = NativeWork(Some("step"), "RepairStep", None, Some("session:repair:3"), Some("completed"))
      native(plan, closed, work :+ repair, Some(facts)).head.firstPass shouldBe Some(false)
      native(plan, closed, work, Some(facts.copy(attempts = facts.attempts.map(_.copy(status = "unknown"))))).head.firstPass shouldBe None

      // Given complete native results and two completed launches of the same
      // original assignment, v1 facts do not prove a retry-free first pass.
      val repeated = facts.copy(attempts = facts.attempts :+ attempt(work.head, "retry"))
      take(bindC(repeated, "session", plan, closed, work)) shouldBe repeated
      val repeatedWork = native(plan, closed, work, Some(repeated))
      repeatedWork.head.firstPass shouldBe None
      repeatedWork.head.executions shouldBe Vector("one", "two", "retry")
      val cohort = Cohort("fixture-repeated", "Step", "unclassified", Some(at), end, "source@1")
      val repeatedSummary = project("session", cohort, repeatedWork, Vector.empty)
      repeatedSummary.eventualSuccess shouldBe 1
      repeatedSummary.terminal shouldBe 1
      repeatedSummary.firstPassSuccess shouldBe 0
      repeatedSummary.unknownFirstPass shouldBe 1
      repeatedSummary.firstPassRate shouldBe None
      english(repeatedSummary) should include("first-pass=0/1 (unavailable)")
    }

    "downgrade destination coverage when known cohort resources are excluded or absent" in {
      import GoalPhaseWorkComparison.*
      val at = "2026-11-02T00:00:00Z"
      val cohort = Cohort("fixture", "Step", "coding", Some(at), at, "source@1")
      val selected = Work("step", "Step", "coding", "accepted", Some(false), None, None, Vector.empty, Vector("E", "F"))
      val foreign = selected.copy(identity = "foreign", unit = "Task", executions = Vector("E"))
      def observation(id: String) = GoalPhasePhaseMetrics.UsageObservation(id, 1, "fixture", id,
        "codex", "worker", None, at, at, Some(10L), Some(20L), inclusion = "self-only", coverage = "complete", status = "completed")
      val projected = project("fixture", cohort, Vector(selected, foreign), Vector(observation("E"), observation("F")))
      val codex = projected.usage.find(_.destination == "codex").get
      codex.totalTokens shouldBe Some(30L)
      codex.coverage shouldBe "partial"
      projected.limitations.exists(_.contains("Cross-cohort")) shouldBe true
      val missing = project("fixture", cohort, Vector(selected), Vector(observation("F")))
      missing.usage.find(_.destination == "codex").get.coverage shouldBe "partial"
      missing.usage.find(_.destination == "codex").get.totalTokens shouldBe Some(30L)
    }
  }

  "Native Phase close and recollection" should {
    "read each Phase record through its exact staging identity while retaining the release scope" in {
      Given("a directory registration followed by the exact literal completion destination")
      val registered = Vector("src", "docs/notes", "docs/notes/completion.md")
      When("the native record owner selects the independently granted bounded read")
      val selected = take(CompletionRecords.phaseReadSelectionC("docs/notes/completion.md", registered))
      Then("the exact goal-path ordinal is selected and directory coverage alone is refused")
      selected.value shouldBe "goal-path-3"
      CompletionRecords.phaseReadSelectionC("docs/notes/completion.md", registered.take(2)).isSuccess shouldBe false
    }

    "write the durable English summary through actual native recording and one release commit" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyPhaseCompletionRecords(this)
    }

    "retain actual original endpoints and comparison after reopening without replaying checks or close" in {
      object Complete extends GoalPhasePostCompletionSyncFixture
      val root = Complete.freshRoot("p610-installed-profile-close")
      var identity = ""
      var terminal = Option.empty[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult]
      Complete.episode(root, initialize = true) { episode =>
        identity = episode.closeDevelopment()._1
        val checks = Complete.phaseExecutions(root)
        val closed = episode.advance(identity)
        closed.kind shouldBe "Terminal"
        closed.outcome shouldBe Some("completed")
        val metrics = io.circe.parser.parse(take(closed.completion.get.metrics.get.toJsonStringC)).toOption.get
        metrics.hcursor.downField("start").get[String]("status").toOption shouldBe Some("available")
        metrics.hcursor.downField("end").get[String]("status").toOption shouldBe Some("available")
        metrics.hcursor.downField("comparison").focus.flatMap(_.asArray).get should not be empty
        terminal = Some(closed)
        Complete.phaseExecutions(root) shouldBe checks
      }
      Files.createDirectories(root.resolve("src"))
      Files.writeString(root.resolve("src/Later.scala"), "unrelated\n")
      Complete.episode(root) { episode =>
        episode.advance(identity) shouldBe terminal.get
        episode.advance(identity) shouldBe terminal.get
      }
    }
  }
}
