package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartCommand
import SplitPhaseExplicitPartitionFixture.take

/** Only test-owned selected runtime programs/inputs are specialized. Actual
  * Git, public Operations, process driver and durable Workflow remain real.
  * @since   Oct.  7, 2026
  * @version Oct.  8, 2026
  */
object ThreeWorkflowGitInterruption {
  def main(args: Array[String]): Unit = {
    require(args.length > 3, "Expected fixed owned root, work boundary, effect and original Git argv")
    val root = Path.of(args(0)).toAbsolutePath.normalize
    // Managed programs run in their runtime-owned WorkArea, not in the SBT
    // caller's repository. The host fixes this absolute test boundary as argv.
    val work = Path.of(args(1)).toAbsolutePath.normalize
    require(root.startsWith(work) && root != work, "The effect must belong to a unique specification root")
    val effect = args(2)
    require(Set("commit", "push", "final-fetch")(effect), "Unknown selected owned effect")
    val argv = args.drop(3).toVector
    require(argv.sliding(2).exists(_ == Vector("-C", root.resolve("repository").toString)), "Git target differs")
    val child = new ProcessBuilder((Vector("/usr/bin/git") ++ argv)*).inheritIO().start()
    val code = child.waitFor()
    if (code != 0) System.exit(code)
    val count = root.resolve(effect + "-invocations.txt")
    Files.writeString(count, ProcessHandle.current().pid().toString + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    val calls = Files.readString(count).linesIterator.size
    if (effect != "final-fetch" || calls == 2) {
      // The real effect is complete, but the managed program has NOT returned
      // its result. The owned caller must be stopped before this program.
      Files.writeString(root.resolve(effect + "-executed.txt"), ProcessHandle.current().pid().toString,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
      Thread.sleep(30000)
      throw new IllegalStateException("Selected effect must be interrupted before a result is returned")
    }
  }
}

final class ThreeWorkflowRepositoryRecoveryOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  "RepositorySync public Operation recovery from an actual Git postimage" should {
    for ((scenario, effect, pendingStage, intentKey, observedKey, executions) <- Vector(
      ("InterruptedCommit", "commit", "NativeCommitPreparing", "commit-intent", "commit-observed", 1),
      ("InterruptedPush", "push", "NativePushPreparing", "push-intent", "push-observed", 1),
      ("InterruptedFinalFetch", "final-fetch", "NativeFinalFetching", "final-fetch-intent", "converged", 2))) {
      ("recover interrupted " + effect + " by observing its real postimage without repeating that effect") in {
        Given("owned diverged repositories and actual managed checks with separately supplied independent review evidence")
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(work, "git-recovery-episode-")
        val command = RepositorySyncStartCommand(ThreeWorkflowNormalHostFixture.definition(root).repository.input,
          "selected-recovery", "participant", "direct-git-recovery-start")
        val identity = take(RepositorySyncManagedFetch.identityC(command))
        val advance = RepositorySyncManagedServiceAssembly.encodeAdvance(identity.value)
        var review = Option.empty[RepositorySyncNativeMergeReview.Request]
        var accepted = Option.empty[InstanceRecord]
        _with_runtime_(root, scenario) { (owner, component, actor, prepare) =>
          def invoke(operation: String, wire: String): String = _scalar_(take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)))
          prepare(call => call.setupC(_setup_commands_(root, scenario).size, Some(root))
            .flatMap(_ => _prepare_input_(call, command, root, merged = true)))
          When("public Start and advance create a real pending merge and issue its independent review")
          take(WorkflowWaitJsonV1.decodeC(invoke("startRepositorySync", RepositorySyncStartServiceOperation.encodeCommand(command))))
          val issued = take(WorkflowWorkOrderJsonV1.decodeC(invoke("advanceRepositorySync", advance), RepositorySyncNativeMergeReview.inputCodec))
          val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
          val input = order.input.get.value
          review = Some(input)
          input.checks.last.exitCode shouldBe Some(0)
          val reference = ContextReference("specification-git-recovery-review", input.validatedRevision)
          val result = ContinuationResult(issued.handle, order.runId, order.continuationId, order.expectedRevision,
            order.context.snapshot, TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
              RepositorySyncNativeMergeReview.Result("accept", "Specification review of the assigned actual candidate", Vector.empty)),
            reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-reviewer")))
          prepare(_.reviewEvidenceC(input, result))
          When("the assigned separately evidenced review is completed through its public Operation")
          take(WorkflowWaitJsonV1.decodeC(invoke("submitNativeMergeReview", take(WorkflowResultJsonV1.encodeC(result,
            RepositorySyncNativeMergeReview.resultCodec)))))
          prepare(call => call.currentC(identity).map(value => { accepted = Some(value); () }))
          Then("the saved native review accepts the candidate but does not claim convergence")
          accepted.get.currentProgression shouldBe Some(ProgressionReference("sync.ReviewAccepted"))
          accepted.get.lifecycle shouldBe Lifecycle.Active
        }
        When("the owned caller JVM is terminated after actual " + effect + " success but before its managed result returns")
        val stopped = _separate_operation_observation_(root, scenario, false, identity, advance, interruptGit = Some(effect))
        val countPath = root.resolve(effect + "-invocations.txt")
        val count = Files.readString(countPath)
        Then("termination is real and only the selected actual Git executions have occurred")
        stopped.hcursor.get[Int]("exit").toOption.get should not be 0
        Files.readString(root.resolve(effect + "-executed.txt")).trim.toLong shouldBe stopped.hcursor.get[Long]("effectPid").toOption.get
        count.linesIterator.size shouldBe executions
        var pending = Option.empty[InstanceRecord]
        var postimage = Option.empty[RepositorySyncManagedGitObservation.Observed]
        _with_runtime_(root, scenario) { (_, _, _, prepare) =>
          var intent = Option.empty[RepositorySyncNativeProgressStore.Stage]
          var observed = Option.empty[RepositorySyncNativeProgressStore.Stage]
          prepare(call => for {
            _ <- call.currentC(identity).map(value => { pending = Some(value); () })
            _ <- call.nativeStageC(command, root, intentKey).map(value => { intent = value; () })
            _ <- call.nativeStageC(command, root, observedKey).map(value => { observed = value; () })
            actual <- call.observeC()
            metadata <- RepositorySyncManagedGitObservation.commitC(call.core, actual.head)
          } yield {
            postimage = Some(actual)
            metadata.parents shouldBe Vector(review.get.localTip, review.get.remoteTip)
          })
          Then("the durable intent exists without an observed-result row, while the actual clean two-parent commit is retained")
          intent should not be empty
          observed shouldBe empty
          pending.get.currentProgression shouldBe Some(ProgressionReference("sync." + pendingStage))
          pending.get.lifecycle shouldBe Lifecycle.Active
          pending.get.history.take(accepted.get.history.size) shouldBe accepted.get.history
          postimage.get.dirty shouldBe empty
          Files.exists(root.resolve("repository/.git/MERGE_HEAD")) shouldBe false
        }
        When("another fresh JVM advances only the saved intent and observes the real repository postimage")
        val recovered = _separate_operation_observation_(root, scenario, false, identity, advance, allowProgress = true)
        val terminal = take(WorkflowTerminalJsonV1.decodeC(recovered.hcursor.get[String]("result").toOption.get,
          RepositorySyncNativeConvergence.resultCodec))
        val result = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[RepositorySyncNativeConvergence.Result]].result.value
        Then("public recovery reaches actual convergence without replaying the interrupted commit, push or final fetch")
        recovered.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(pending.get))
        result.tip shouldBe postimage.get.head
        result.tree shouldBe postimage.get.tree
        Files.readString(countPath) shouldBe count
        val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
        val completed = take(WorkflowInstanceRecordJsonV1.decodeC(recovered.hcursor.get[String]("after").toOption.get, catalog))
        completed.lifecycle shouldBe Lifecycle.Completed
        completed.history.take(pending.get.history.size) shouldBe pending.get.history
        _with_runtime_(root, scenario) { (_, _, _, prepare) =>
          var actual = Option.empty[RepositorySyncManagedGitObservation.Observed]
          prepare(call => call.observeC().map(value => { actual = Some(value); () }))
          actual.get.head shouldBe result.tip
          actual.get.trackingTip shouldBe Some(result.tip)
          actual.get.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
          actual.get.dirty shouldBe empty
        }
        When("a further JVM reopens the complete terminal without reinstalling input or native history")
        val reopened = _separate_operation_observation_(root, scenario, false, identity, advance)
        Then("terminal, complete native history, preserved files and actual execution count remain exact")
        reopened.hcursor.get[String]("result").toOption.get shouldBe recovered.hcursor.get[String]("result").toOption.get
        reopened.hcursor.get[String]("before").toOption.get shouldBe recovered.hcursor.get[String]("after").toOption.get
        Files.readString(countPath) shouldBe count
        Files.readString(root.resolve("repository/local.txt")) shouldBe "local addition\n"
        Files.readString(root.resolve("repository/remote.txt")) shouldBe "remote addition\n"
      }
    }
  }
}
