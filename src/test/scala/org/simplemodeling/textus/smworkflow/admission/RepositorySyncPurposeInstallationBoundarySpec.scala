package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import WorkflowManagedProcessExecution as E

/** Local owner/store regression with actual typed Git and SQLite. No nested
  * build, installed CLI, timing fault or successful installation is required.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPurposeInstallationBoundarySpec extends AnyWordSpec with GivenWhenThen
    with RepositorySyncMaintenanceFixture {
  private val text = "Independent document candidate.\n"
  private final case class Source(head: String, index: String, content: String)

  private def encoded(record: InstanceRecord): String = take(WorkflowInstanceRecordJsonV1.encodeC(record))
  private def history(record: InstanceRecord): Vector[Json] =
    parser.parse(encoded(record)).toOption.get.hcursor.get[Vector[Json]]("history").toOption.get
  private def stageJson(stage: N.Stage): Json = Json.obj(
    "record" -> parser.parse(encoded(stage.record)).toOption.get, "payload" -> N.codec.encode(stage.payload))

  private final class Episode(val root: Path, val input: V.RepositorySyncStartInput,
    call: (String, List[Property]) => Consequence[OperationResponse], prepare: (Setup => Consequence[Unit]) => Unit) {
    val repository: Path = root.resolve("repository")
    val identity: InstanceIdentity = InstanceIdentity(invoke[V.RepositorySyncStartResult]("startRepositorySync",
      List(Property("input", Record.data("repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))).sessionId)

    def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
      read_value[A](take(call(operation, properties)))
    def advanceC(): Consequence[OperationResponse] = call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(identity.value))
    def advance(): V.RepositorySyncAdvanceResult = read_value[V.RepositorySyncAdvanceResult](take(advanceC()))
    def current(): InstanceRecord = {
      var found = Option.empty[InstanceRecord]
      prepare(_.currentC(identity).map(value => { found = Some(value); () }))
      found.get
    }
    def withStoreC[A](setup: Setup)(run: N.Store => Consequence[A]): Consequence[A] = for {
      definition <- RepositorySyncDefinitionBinding.bindC
      declaration = RepositorySyncManagedFetch.Declaration(_configuration_, _collection_("instances"),
        _collection_("continuations"), input, Target(repository, "main", "origin", "main", root.resolve("upstream.git").toString))
      session <- RepositorySyncManagedFetch.sessionC(identity.value, declaration, definition, setup.core)
      store <- N.bindC(session, declaration, setup.core, _ => Consequence.unit)
      result <- run(store)
    } yield result
    def stage(key: String): Option[N.Stage] = {
      var found = Option.empty[N.Stage]
      prepare(setup => withStoreC(setup)(_.loadC(key)).map(value => { found = value; () }))
      found
    }
    private def sourceC(setup: Setup): Consequence[Source] = for {
      head <- E.executeGitC(setup.core, GitOperation.Head).flatMap(_.valueC)
      index <- E.executeGitC(setup.core, GitOperation.IndexTree).flatMap(_.valueC)
    } yield Source(head.value, index.value, Files.readString(repository.resolve(purpose_path)))
    def source(): Source = {
      var found = Option.empty[Source]
      prepare(setup => sourceC(setup).map(value => { found = Some(value); () }))
      found.get
    }
    def work[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A]): WorkflowInteraction[A, Nothing] = {
      var found = Option.empty[WorkflowInteraction[A, Nothing]]
      prepare(_.repositoryWorkC(identity, operation, codec).map(value => { found = Some(value); () }))
      found.get
    }

    /** Each semantic result is submitted exactly once through its real issued owner. */
    def reviewedCandidate(): (N.Stage, ContextReference) = {
      advance().submitOperation shouldBe Some("submitRepositoryPurposeResult")
      val purpose = work(RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
      val request = purpose.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
      request.input.get.value.files.find(_.path == purpose_path).get.worktree shouldBe Some(text)
      val reference = ContextReference("installation-boundary-purpose", "1")
      val reply = ContinuationResult(purpose.handle, request.runId, request.continuationId, request.expectedRevision,
        request.context.snapshot, TypedValue(request.resultType.get.value, grouping(text)), reference, Vector.empty,
        ExecutionEvidence(Vector(reference), Some("specification-purpose-author")))
      invoke[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
        take(NativeWorkflowOperationInputFixture.submissionC(reply))).accepted shouldBe true
      val issued = advance()
      issued.submitOperation shouldBe Some("submitNativeMergeReview")
      val checked = stage("purpose-1-assessment").get
      checked.payload.stage shouldBe "PurposeChecked"
      checked.payload.purposes.get.assessment.get.branch shouldBe "detailed-review"
      val review = work(RepositorySyncNativeMergeReviewContinuation.completionOperation, RepositorySyncNativeMergeReview.inputCodec)
      val assigned = review.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
      val judged = ContextReference("installation-boundary-independent-review", "1")
      val verdict = ContinuationResult(review.handle, assigned.runId, assigned.continuationId, assigned.expectedRevision,
        assigned.context.snapshot, TypedValue(assigned.resultType.get.value, RepositorySyncNativeMergeReview.Result(
          "accept", "Fixture-owned independent review of the exact actual document candidate", Vector.empty)),
        judged, Vector.empty, ExecutionEvidence(Vector(judged), Some("specification-independent-reviewer")))
      prepare(_.reviewEvidenceC(assigned.input.get.value, verdict))
      invoke[V.RepositorySyncNativeMergeReviewSubmissionResult]("submitNativeMergeReview",
        take(NativeWorkflowOperationInputFixture.submissionC(verdict))).accepted shouldBe true
      (checked, judged)
    }

    def retainInstalling(checked: N.Stage, review: ContextReference): N.Stage = {
      var found = Option.empty[N.Stage]
      prepare(setup => withStoreC(setup) { store => for {
        actual <- setup.currentC(identity)
        admitted <- store.saveC("purpose-1-review-admitted", "purpose-progress", actual,
          checked.payload.copy(purposes = checked.payload.purposes.map(_.copy(review = Some(review)))))
        intent <- store.saveC("purpose-1-commit-intent", "purpose-progress", admitted.record,
          admitted.payload.copy(stage = "PurposeInstalling"))
      } yield { found = Some(intent); () } })
      found.get
    }

    def collectWithoutCommitAdmission(intent: N.Stage): Vector[V.RepositorySyncAdvanceResult] = {
      val sourceBeforeProbe = source()
      val recordBeforeProbe = encoded(current())
      val intentBeforeProbe = stageJson(stage("purpose-1-commit-intent").get)
      var found = Vector.empty[V.RepositorySyncAdvanceResult]
      prepare(setup => withStoreC(setup) { store =>
        var commitAdmissions = 0
        val tripwire = new RepositorySyncNativeConvergence.AuthorityPort {
          def admitC(core: ActionCall.Core, context: RepositorySyncManagedFetch.StartContext,
            target: Target, effect: RepositorySyncNativeConvergence.Effect): Consequence[Unit] = {
            effect shouldBe RepositorySyncNativeConvergence.Effect.PurposeCommit
            commitAdmissions += 1
            Consequence.stateConflict("Fixture refuses purpose commit admission")
          }
        }
        for {
          before <- setup.currentC(identity)
          _ = encoded(before) shouldBe recordBeforeProbe
          // Exercise only the owner's mandatory admission seam; this probe runs no Git.
          probe = tripwire.admitC(setup.core, store.context, store.declaration.target,
            RepositorySyncNativeConvergence.Effect.PurposeCommit)
          _ = probe.isSuccess shouldBe false
          _ = commitAdmissions shouldBe 1
          sourceAfterProbe <- sourceC(setup)
          recordAfterProbe <- setup.currentC(identity)
          intentAfterProbe <- store.loadC("purpose-1-commit-intent")
          _ = sourceAfterProbe shouldBe sourceBeforeProbe
          _ = encoded(recordAfterProbe) shouldBe recordBeforeProbe
          _ = stageJson(intentAfterProbe.get) shouldBe intentBeforeProbe
          _ = { commitAdmissions = 0 }
          observed <- RepositorySyncManagedGitObservation.observeC(setup.core)
          _ = encoded(before) shouldBe encoded(intent.record)
          first <- RepositorySyncPurposeRuntime.advanceC(store, recordAfterProbe, observed, None, Some(tripwire), None,
            (_, _) => Consequence.stateConflict("Installing UNKNOWN must not reach completion"))
          middle <- setup.currentC(identity)
          second <- RepositorySyncPurposeRuntime.advanceC(store, middle, observed, None, Some(tripwire), None,
            (_, _) => Consequence.stateConflict("Installing UNKNOWN must not reach completion"))
          after <- setup.currentC(identity)
          retained <- store.loadC("purpose-1-commit-intent")
          sourceAfterCollection <- sourceC(setup)
          _ = commitAdmissions shouldBe 0
          _ = sourceAfterCollection shouldBe sourceBeforeProbe
          _ = encoded(middle) shouldBe encoded(before)
          _ = encoded(after) shouldBe encoded(before)
          _ = stageJson(retained.get) shouldBe stageJson(intent)
        } yield { found = Vector(first, second); () }
      })
      found
    }
  }

  private def withEpisode(name: String)(run: Episode => Unit): Unit = {
    val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/purpose-installation-boundary").toAbsolutePath), name + "-")
    _with_runtime_(root, "Equal", hostParameters = maintenance_parameters(review = true)) { (owner, component, actor, prepare) =>
      prepare(_.setupC(_setup_commands_(root, "Equal").size))
      Files.writeString(root.resolve("repository").resolve(purpose_path), text)
      val episode = new Episode(root, ThreeWorkflowNormalHostFixture.definition(root).repository.input,
        (operation, properties) => owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = operation, properties = properties), actor), prepare)
      run(episode)
    }
  }

  "Purpose installation boundary" should {
    "refuse candidate drift after accepted required review before recording an installing intent" in withEpisode("candidate-drift") { episode =>
      Given("one real prepared checked document candidate and its accepted independent required review")
      val (checked, review) = episode.reviewedCandidate()
      val originalReceipt = stageJson(checked)
      val before = episode.current()
      val source = episode.source()
      val candidateFile = episode.root.resolve("purpose-slot-1").resolve(purpose_path)
      Files.readString(candidateFile) shouldBe text
      Files.writeString(candidateFile, text + "Unaccepted candidate drift.\n")
      When("normal public Advance observes the changed candidate before installation")
      val refused = episode.advanceC()
      Then("the real workspace guard refuses before any installing intent and preserves source and original evidence")
      refused.isSuccess shouldBe false
      refused.toString should include("exact clean candidate")
      episode.stage("purpose-1-commit-intent") shouldBe None
      episode.stage("purpose-1-installed") shouldBe None
      stageJson(episode.stage("purpose-1-assessment").get) shouldBe originalReceipt
      val admitted = episode.stage("purpose-1-review-admitted").get
      admitted.payload.purposes.get.review shouldBe Some(review)
      admitted.payload.purposes.get.candidate shouldBe checked.payload.purposes.get.candidate
      history(episode.current()).take(before.history.size) shouldBe history(before)
      episode.source() shouldBe source
      Files.readString(candidateFile) shouldBe text + "Unaccepted candidate drift.\n"
    }

    "collect a retained installing intent twice without dispatching a commit or changing native history" in withEpisode("installing-unknown") { episode =>
      Given("a real checked candidate and accepted review with an explicitly fixture-retained native installing intent but no result")
      val (checked, review) = episode.reviewedCandidate()
      val source = episode.source()
      val originalReceipt = stageJson(checked)
      val intent = episode.retainInstalling(checked, review)
      val before = encoded(episode.current())
      intent.payload.stage shouldBe "PurposeInstalling"
      intent.payload.purposes.get.candidate shouldBe checked.payload.purposes.get.candidate
      episode.stage("purpose-1-installed") shouldBe None
      When("the normal Purpose owner advances twice with a proven purpose-commit admission tripwire")
      val observations = episode.collectWithoutCommitAdmission(intent)
      Then("both collections are passive waits and preserve the exact serialized Session candidate receipts and source")
      observations.size shouldBe 2
      observations.foreach { value =>
        value.kind shouldBe "Wait"
        value.condition shouldBe Some("purpose-effect-unknown")
      }
      encoded(episode.current()) shouldBe before
      stageJson(episode.stage("purpose-1-commit-intent").get) shouldBe stageJson(intent)
      stageJson(episode.stage("purpose-1-assessment").get) shouldBe originalReceipt
      episode.stage("purpose-1-installed") shouldBe None
      episode.stage("purpose-1-commit-result") shouldBe None
      episode.stage("purpose-1-commit-intent").get.payload.purposes.get.commits shouldBe checked.payload.purposes.get.commits
      episode.source() shouldBe source
    }
  }
}
