package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, StandardOpenOption}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Automated Host stand-ins; only the separately recorded live run proves Codex execution.
 * @since   Sep. 29, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseWorkerFixtureHostSpec extends AnyWordSpec with Matchers {
  import GoalPhaseApplicationFixture.take
  import GoalPhaseWorkerFixtureHost.*

  "GoalPhase worker Host fixture" should {
    "publish private readiness and consume only a fully published regular completion file" in {
      // Given a fresh fixture-owned transport directory.
      val directory = Files.createTempDirectory("smwf-worker-host-")
      try {
        val transport = GoalPhaseWorkerFixtureTransport.open(directory.toString)
        GoalPhaseWorkerFixtureTransport.emit(transport, "issued.json", "{\"kind\":\"issued-work-order\"}")
        // When the Host payload is written completely before its ready marker.
        Files.writeString(directory.resolve("completion.json"), "{\"result\":{}}\n", StandardOpenOption.CREATE_NEW)
        Files.writeString(directory.resolve("completion-ready"), "ready\n", StandardOpenOption.CREATE_NEW)
        // Then exactly those bytes are consumed and the directory cannot be reused.
        GoalPhaseWorkerFixtureTransport.awaitCompletion(transport, 0L) shouldBe "{\"result\":{}}"
        Files.readString(directory.resolve("issued.json")) shouldBe "{\"kind\":\"issued-work-order\"}\n"
        intercept[IllegalArgumentException](GoalPhaseWorkerFixtureTransport.open(directory.toString))
        intercept[IllegalArgumentException](GoalPhaseWorkerFixtureTransport.emit(transport, "other.json", "{}"))
      } finally {
        Vector("issued.json", "completion.json", "completion-ready").foreach(name => Files.deleteIfExists(directory.resolve(name)))
        Files.delete(directory)
      }
    }

    "timeout without a completion and reject symlinked input without consuming it" in {
      // Given a fresh transport with no input.
      val directory = Files.createTempDirectory("smwf-worker-host-")
      try {
        val transport = GoalPhaseWorkerFixtureTransport.open(directory.toString)
        // When input is absent or the completion path is a symlink.
        intercept[IllegalArgumentException](GoalPhaseWorkerFixtureTransport.awaitCompletion(transport, 0L))
        Files.writeString(directory.resolve("payload.json"), "{}", StandardOpenOption.CREATE_NEW)
        Files.createSymbolicLink(directory.resolve("completion.json"), directory.resolve("payload.json"))
        Files.writeString(directory.resolve("completion-ready"), "ready", StandardOpenOption.CREATE_NEW)
        // Then the filesystem transport fails closed without following the link.
        intercept[IllegalArgumentException](GoalPhaseWorkerFixtureTransport.awaitCompletion(transport, 0L))
      } finally {
        Vector("completion.json", "completion-ready", "payload.json").foreach(name => Files.deleteIfExists(directory.resolve(name)))
        Files.delete(directory)
      }
    }

    "normalize semantic completion without consuming it until the public Operation is called" in {
      // Given a Start-created instance with an issued common WorkOrder.
      val host = new GoalPhaseWorkerFixtureHost("host-test-model")
      try {
        val before = host.fixture.stored
        val payload = GoalPhaseReviewCompletionOperation.payloadCodec.encode(take(deterministicC(host.fixture)))
        // When a Host-attested completion is normalized and passed to the public CLI Operation.
        val normalized = take(host.normalizeC("host-test-worker", "host-test-model", payload))
        host.fixture.stored shouldBe before
        val wire = take(host.submitC(normalized))
        val decision = take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec))
        // Then semantic data remains exact and the same instance stops at missing authority.
        decision.handle shouldBe host.started.handle
        host.fixture.saved.submitted shouldBe normalized
        normalized.evidence.workerIdentity shouldBe Some("host-test-worker")
        normalized.evidence.modelIdentity shouldBe Some("host-test-model")
        host.fixture.stored.revision.value shouldBe 3L
        host.fixture.commits shouldBe empty
        host.submitC(normalized) shouldBe a[Consequence.Failure[?]]
      } finally host.close()
    }

    "reject a wrong model, incomplete payload and invented next-state field without progressing" in {
      // Given the frozen worker selection and original live instance.
      val host = new GoalPhaseWorkerFixtureHost("host-test-model")
      try {
        val before = host.fixture.stored
        val payload = GoalPhaseReviewCompletionOperation.payloadCodec.encode(take(deterministicC(host.fixture)))
        // When execution identity or semantic payload violates the admission contract.
        host.normalizeC("host-test-worker", "wrong-model", payload) shouldBe a[Consequence.Failure[?]]
        host.normalizeC("", "host-test-model", payload) shouldBe a[Consequence.Failure[?]]
        host.normalizeC("host-test-worker", "host-test-model", Json.obj()) shouldBe a[Consequence.Failure[?]]
        host.normalizeC("host-test-worker", "host-test-model",
          payload.mapObject(_.add("nextState", Json.fromString("StepClosed")))) shouldBe a[Consequence.Failure[?]]
        // Then no completion, transition or commit is fabricated.
        host.fixture.stored shouldBe before
        host.fixture.commits shouldBe empty
      } finally host.close()
    }

    "preserve Workflow transitions with the fixed known-candidate Provider and omit AI metadata" in {
      // Given equivalent fixtures and the Provider's independently defined known candidate.
      val host = new GoalPhaseWorkerFixtureHost("host-test-model")
      val provider = new GoalPhaseApplicationFixture(viaCli = true, fromPublicStart = true,
        withAuthority = true, programText = program, planningText = planning)
      try {
        val started = take(provider.start())
        provider.associateClose(started.handle)
        val issued = provider.firstReview()
        val value = take(deterministicC(provider))
        // When both use the same ReviewStepResult contract through public Operations.
        val external = take(host.normalizeC("host-test-worker", "host-test-model",
          GoalPhaseReviewCompletionOperation.payloadCodec.encode(value)))
        take(host.submitC(external))
        val deterministic = provider.result(issued, value, "ReviewStepResult",
          ContextReference("deterministic-review-result", provider.originalCandidate))
        val wire = take(provider.invoke("submitStepReview", provider.reviewWire(deterministic)))
        // Then definitions, revisions and progression match without an AI-shaped receipt.
        take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec)).handle shouldBe started.handle
        host.started.handle.workflowIdentity shouldBe started.handle.workflowIdentity
        host.started.handle.workflowRevision shouldBe started.handle.workflowRevision
        trace(host.fixture) shouldBe trace(provider)
        deterministic.evidence shouldBe ExecutionEvidence(Vector.empty)
        host.fixture.commits shouldBe empty
        provider.commits shouldBe empty
        provider.program = "unknown program".getBytes("UTF-8").toVector
        deterministicC(provider) shouldBe a[Consequence.Failure[?]]
      } finally { provider.subsystem.shutdown(); host.close() }
    }
  }
}
