package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.record.Record
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.runtime.SbtValidationProgram
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import SplitPhaseExplicitPartitionFixture.take

/** Explicitly selected only by the scripted harness, never ordinary test/testOnly
  * discovery. That harness owns the actual SBT children under one shared lock.
  */
@DoNotDiscover
/**
 * @since   Oct.  7, 2026
  * @version Oct. 12, 2026
 */
final class ThreeWorkflowSbtOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private def _owned_scripted_(): Path = {
    val owner = sys.props.getOrElse("smworkflow.spec.owner", fail("Use runtimeScripted/scripted, not testOnly" )).toLong
    var cursor = Option(ProcessHandle.current())
    var ancestors = Vector.empty[Long]
    while (cursor.nonEmpty) {
      ancestors :+= cursor.get.pid()
      val parent = cursor.get.parent()
      cursor = if (parent.isPresent) Some(parent.get()) else None
    }
    ancestors should contain(owner)
    owner should not be ProcessHandle.current().pid()
    Path.of(sys.props.getOrElse("smworkflow.spec.launcher", fail("Missing scripted SBT launcher")))
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val original = super._setup_commands_(root, scenario)
    require(Set("Equal", "Goal")(scenario), "Only this owned selected build is admitted")
    original.take(2) ++ Vector(Vector("-C", root.resolve("repository").toString, "add", "--", ".")) ++ original.drop(2)
  }
  override protected def _validation_programs_(root: Path, scenario: String,
    defaults: Vector[ProcessProgramDefinition], java: String): Vector[Json] = {
    val limits = ProcessExecutionLimits(Some(10000), Some(90000), Some(1000), Some(2097152),
      Some(131072), Some(16384), Some(16), Some(131072), Some(1), Some(131072), Some(4194304))
    val definition = take(SbtValidationProgram.definitionC(1, root.resolve("repository"), java,
      _owned_scripted_(), _classpath_(), Vector("test"), limits, Map("PATH" -> "/usr/bin:/bin")))
    Vector(_program_(definition, java, Map("PATH" -> "/usr/bin:/bin"), 90000))
  }
  private def _build_(root: Path, successful: Boolean): Unit = {
    val repository = Files.createDirectories(root.resolve("repository"))
    Files.createDirectories(repository.resolve("project"))
    Files.createDirectories(repository.resolve("src/test/scala"))
    Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
    Files.writeString(repository.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
    Files.writeString(repository.resolve("value.txt"), if (successful) "expected\n" else "incorrect\n")
    Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.18"
      |Test / test := (Test / runMain).toTask(" ProductCheck").value
      |""".stripMargin)
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """object ProductCheck {
      |  def main(args: Array[String]): Unit = {
      |    val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |    val marker = root.getParent.resolve("sbt-executions.txt")
      |    java.nio.file.Files.write(marker, (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
      |      java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |    println("PRODUCT_CHECK_ENTERED " + root)
      |    assert(java.nio.file.Files.readString(root.resolve("value.txt")) == "expected\n", "actual product input differs")
      |    println("PRODUCT_CHECK_PASSED")
      |  }
      |}
      |""".stripMargin)
  }
  "Direct RepositorySync Operations with the runtime SBT adapter" should {
    for (successful <- Vector(true, false)) {
      (if (successful) "consume actual successful SBT test output and converge"
        else "consume an actual failed product test and wait without push") in {
        Given("one scripted-owned invocation, normal caches, a real selected build and managed runtime capabilities")
        _owned_scripted_()
        val directory = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(directory, "sbt-episode-")
        _build_(root, successful)
        val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
        val selection = _repository_selection_(input, "selected-repository", "direct-sbt-start")
        val identity = take(RepositorySyncManagedFetch.identityC(selection.selectionId, selection.idempotencyKey))
        val advance = NativeWorkflowOperationInputFixture.advance()
        for (session <- Vector("execute")) _with_runtime_(root, "Equal") { (owner, component, actor, prepare) =>
          def invoke(operation: String, input: String): String = _scalar_(take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
              arguments = List(Argument("arg1", input))), actor)))
          if (session == "execute") {
            prepare(call => call.setupC(_setup_commands_(root, "Equal").size).flatMap(_ => _prepare_input_(call, input, selection, root)))
            take(WorkflowWaitJsonV1.decodeC(invoke("startRepositorySync", NativeWorkflowOperationInputFixture.repository(input))))
          }
          When("public advance runs only the selected fixed SBT test, or observes its retained result in " + session)
          val result = invoke("advanceRepositorySync", advance)
          var current = Option.empty[InstanceRecord]
          var validation = Option.empty[RepositorySyncNativeMergeValidation.State]
          var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
          prepare(call => call.currentC(identity).flatMap(value => { current = Some(value)
            call.validationC(input, selection, root, if (successful) "integration-validated" else "validation-result-1")
              .flatMap(value => { validation = Some(value); call.observeC().map(value => { observed = Some(value); () }) })
          }))
          Then("the durable receipt contains the real compiled product test and actual launcher exit, not a claimed success")
          val receipt = validation.get.receipts.last
          receipt.identity shouldBe "test"
          receipt.termination shouldBe "exited"
          receipt.stdout should include("PRODUCT_CHECK_ENTERED " + root.resolve("repository"))
          if (successful) {
            receipt.exitCode shouldBe Some(0)
            receipt.stdout should include("PRODUCT_CHECK_PASSED")
            take(WorkflowTerminalJsonV1.decodeC(result, RepositorySyncNativeConvergence.resultCodec))
            current.get.lifecycle shouldBe Lifecycle.Completed
          } else {
            receipt.exitCode.get should not be 0
            (receipt.stdout + receipt.stderr) should include("actual product input differs")
            receipt.stdout should not include "PRODUCT_CHECK_PASSED"
            take(WorkflowWaitJsonV1.decodeC(result))
            current.get.lifecycle shouldBe Lifecycle.Active
            observed.get.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
          }
          val actual = Files.readString(root.resolve("sbt-executions.txt"))
          actual.linesIterator.size shouldBe 1
          actual.trim.toLong should not be ProcessHandle.current().pid()
        }
      }
    }
  }
  "Direct GoalPhase Operations with the runtime SBT adapter" should {
    "validate the Step and whole Phase, commit and converge through RepositorySync" in {
      Given("one scripted-owned invocation and a real selected one-Step build with separately supplied semantic evidence")
      _owned_scripted_()
      val directory = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(directory, "sbt-goal-episode-")
      _build_(root, true)
      Files.writeString(root.resolve("repository/value.txt"), "initial\n")
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = Vector("value.txt")))
      val plan = O.Plan("selected-sbt-goal-plan", "1", host.goal.input.phaseIdentity,
        host.goal.input.repositoryIdentity, Vector(O.Step("step-1", "Correct selected product input",
          Vector("value.txt"), Vector.empty, Vector("test"))), Vector("test"), repairLimit = 1)
      val selection = _goal_selection_(host.goal.input, "selected-sbt-goal", "direct-sbt-goal-start")
      val identity = take(GoalPhaseNativeRun.identityC(selection.selectionId, selection.idempotencyKey))
      val advance = NativeWorkflowOperationInputFixture.advance()
      var assigned = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var base = Option.empty[O.Snapshot]
      var phaseReviewReference = Option.empty[ContextReference]
      def executions(): Vector[Long] = if (Files.exists(root.resolve("sbt-executions.txt")))
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.map(_.toLong).toVector else Vector.empty
      for (session <- Vector("issue", "implement", "step-review", "phase-review", "close"))
        _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
          def invoke(operation: String, input: String): String = _scalar_(take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
              arguments = List(Argument("arg1", input))), actor)))
          def current(): InstanceRecord = {
            var value = Option.empty[InstanceRecord]
            prepare(call => call.goalCurrentC(identity).map(record => { value = Some(record); () }))
            value.get
          }
          def input(): S.Request = assigned.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
          def next(): Unit = {
            assigned = Some(take(WorkflowWorkOrderJsonV1.decodeC(invoke("advanceGoalPhase", advance), S.inputCodec)))
          }
          def result(outcome: String): ContinuationResult[S.Result] = {
            val work = assigned.get
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
            val selected = order.input.get.value
            val reference = ContextReference("specification-sbt-" + selected.kind, selected.sourceRevision)
            ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
              TypedValue(S.resultCodec.typeIdentity, S.Result(outcome, "Specification response to assigned work only", Vector.empty)),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some(
                if (selected.kind.startsWith("Review")) "specification-reviewer" else "participant")))
          }
          if (session == "issue") {
            prepare(call => for {
              _ <- call.setupC(_setup_commands_(root, "Goal").size)
              _ <- call.createC("goal_selection", selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
                take(ProfileSelectionJsonV1.encodeStorageC(selection))))
              _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
                take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(
                  selection.selectionId, selection.participantIdentity, plan)))))
            } yield ())
            When("public Start captures the real clean base and owning advance issues only implementation")
            take(WorkflowWaitJsonV1.decodeC(invoke("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input))))
            next()
            Then("no SBT check has run before actual implementation")
            input().kind shouldBe "ImplementStep"
            input().selectedPlan shouldBe plan
            base = Some(input().originalBase)
            executions() shouldBe empty
          } else if (session == "close") {
            When("Phase review completion alone is advanced without separately prepared effect authority")
            take(WorkflowWaitJsonV1.decodeC(invoke("advanceGoalPhase", advance)))
            val pending = current()
            pending.currentProgression shouldBe Some(ProgressionReference("phase.PhaseClosing"))
            executions().size shouldBe 2
            Given("test-owned Phase closure evidence and separately selected RepositorySync input")
            val invocation = identity.value + ":repository-closure:" + pending.revision.value
            val closure = GoalPhaseNativeRepositoryClosure.Input(identity.value, pending.revision.value.toString,
              plan, input().originalBase, input().completed, input().candidate.get, phaseReviewReference.get.identity, invocation)
            val child = _repository_selection_(host.repository.input, host.repository.closureSelection, invocation)
            prepare(call => call.evidenceC(NativeHostEvidence.Purpose.PhaseClosure,
              ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-sbt-phase-authority", "1"),
              "participant", take(ThreeWorkflowHostEvidencePorts.phaseClosureBindingC(call.core, closure)))
              .flatMap(_ => _prepare_input_(call, host.repository.input, child, root)))
            When("owning advance executes the installed RepositorySync Operations and their actual SBT check")
            val wire = invoke("advanceGoalPhase", advance)
            val outcome = take(WorkflowTerminalJsonV1.decodeC(wire, GoalPhaseNativeRepositoryClosure.resultCodec))
            val actual = outcome.current.asInstanceOf[WorkflowContinuation.Terminal[GoalPhaseNativeRepositoryClosure.Result]].result.value
            var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
            var checked = Option.empty[RepositorySyncNativeMergeValidation.State]
            prepare(call => call.observeC().flatMap(value => { observed = Some(value)
              call.validationC(host.repository.input, child, root).map(value => { checked = Some(value); () }) }))
            Then("the whole selected Phase retains its actual Step commit, converged repository and third real test receipt")
            actual.plan shouldBe plan
            actual.originalBase shouldBe base.get
            actual.completed.size shouldBe 1
            actual.completed.head.commit.parent shouldBe base.get.head
            actual.completed.head.commit.identity should not be base.get.head
            actual.repository.tip shouldBe actual.reviewedCandidate.head
            observed.get.head shouldBe actual.repository.tip
            observed.get.trackingTip shouldBe Some(actual.repository.tip)
            observed.get.dirty shouldBe empty
            checked.get.receipts.last.exitCode shouldBe Some(0)
            checked.get.receipts.last.stdout should include("PRODUCT_CHECK_PASSED")
            executions().size shouldBe 3
            executions().distinct.size shouldBe 3
            executions() should not contain ProcessHandle.current().pid()
            current().lifecycle shouldBe Lifecycle.Completed
          } else {
            When("a freshly opened runtime supplies the assigned task for its semantic result")
            next()
            val selected = input()
            selected.selectedPlan shouldBe plan
            selected.originalBase shouldBe base.get
            if (session == "implement") {
              Given("the specification implements only the assigned product input, without supplying test receipts")
              Files.writeString(root.resolve("repository/value.txt"), "expected\n")
              val completion = result("completed")
              When("public completion records implementation and owning advance runs the selected Step SBT test")
              take(WorkflowWaitJsonV1.decodeC(invoke("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.encodeC(completion, S.resultCodec)))))
              next()
              Then("independent Step review contains the actual native-cwd SBT output and exit")
              input().kind shouldBe "ReviewStep"
              input().review.get.changedPaths shouldBe Vector("value.txt")
              input().review.get.checks.last.exitCode shouldBe Some(0)
              input().review.get.checks.last.stdout should include("PRODUCT_CHECK_ENTERED " + root.resolve("repository"))
              input().review.get.checks.last.stdout should include("PRODUCT_CHECK_PASSED")
              executions().size shouldBe 1
            } else {
              selected.kind shouldBe (if (session == "step-review") "ReviewStep" else "ReviewPhase")
              executions().size shouldBe (if (session == "step-review") 1 else 2)
              val completion = result("accept")
              prepare(call => ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, selected, completion)
                .flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalReview,
                  ThreeWorkflowHostEvidencePorts.key(completion.resultReference.identity, completion.resultReference.revision),
                  completion.resultReference, "specification-reviewer", binding)))
              When("public completion accepts only the assigned, separately evidenced review")
              take(WorkflowWaitJsonV1.decodeC(invoke("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.encodeC(completion, S.resultCodec)))))
              if (session == "step-review") {
                val pending = current()
                val closure = GoalPhaseNativeClosure.Input(identity.value, pending.revision.value.toString, plan.identity, plan.revision,
                  selected.stepIdentity.get, selected.generation, selected.originalBase, selected.candidate.get, completion.resultReference.identity)
                prepare(call => call.evidenceC(NativeHostEvidence.Purpose.StepClosure,
                  ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-sbt-step-authority", "1"),
                  "participant", take(ThreeWorkflowHostEvidencePorts.stepClosureBindingC(call.core, closure))))
                When("owning advance commits the actual Step and runs the whole-Phase SBT test")
                next()
                Then("whole-Phase review contains a real second check and the retained original allowance")
                input().kind shouldBe "ReviewPhase"
                input().completed.size shouldBe 1
                input().selectedPlan.repairLimit shouldBe 1
                input().review.get.checks.last.exitCode shouldBe Some(0)
                input().review.get.checks.last.stdout should include("PRODUCT_CHECK_PASSED")
                executions().size shouldBe 2
              } else {
                phaseReviewReference = Some(completion.resultReference)
              }
            }
          }
        }
    }
  }
}
