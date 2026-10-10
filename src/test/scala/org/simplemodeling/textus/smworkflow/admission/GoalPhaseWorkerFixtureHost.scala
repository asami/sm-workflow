package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardOpenOption}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepReviewEvidence}

/** Test-only Host bridge; not a second public CLI, scheduler, or Provider API.
  * The worker supplies only ReviewStepResult. The Host owns the issued envelope,
  * mapping policy and observed execution identity; the Workflow owns progression.
  * @since   Sep. 29, 2026
  *  version Oct.  8, 2026
  * @version Oct.  9, 2026
  */
private[admission] final class GoalPhaseWorkerFixtureHost(model: String) extends AutoCloseable {
  import GoalPhaseApplicationFixture.take
  import GoalPhaseWorkerFixtureHost.*

  val fixture = new GoalPhaseApplicationFixture(viaCli = true, fromPublicStart = true,
    withAuthority = true, programText = program, planningText = planning)
  val started = take(fixture.start())
  fixture.associateClose(started.handle)
  val issued = fixture.firstReview()
  val profile = WorkflowWorkerMapping.Profile("codex-reviewer", model,
    Set("review-step-capability"), Set("standard"), reviewCapable = true)
  val policy = WorkflowWorkerMapping.Policy("goal-phase-worker-fixture-v1",
    Vector(WorkflowWorkerMapping.Mapping(ReasoningLevel.Deep, profile)))
  val dispatch = take(WorkflowWorkerMapping.selectC(issued, policy, GoalPhaseStepCloseJson.payloadCodec))

  def issuedJson: Json = Json.obj(
    "kind" -> Json.fromString("issued-work-order"),
    "workOrder" -> take(parseC(take(WorkflowWorkOrderJsonV1.encodeC(issued, GoalPhaseStepCloseJson.payloadCodec)))),
    "selectedWorkerProfile" -> Json.fromString(profile.identity),
    "mappingPolicyVersion" -> Json.fromString(policy.version),
    "modelIdentity" -> Json.fromString(profile.modelIdentity),
    "candidateRevision" -> Json.fromString(fixture.originalCandidate),
    "program" -> Json.fromString(program), "planning" -> Json.fromString(planning),
    "logicalReviewTime" -> Json.fromString("2026-09-28T00:00:00Z")
  )

  def normalizeC(worker: String, observedModel: String, payload: Json): Consequence[ContinuationResult[ReviewStepResult]] =
    GoalPhaseReviewCompletionOperation.payloadCodec.decode(payload).fold(Consequence.stateConflict(_), value => {
      val request = fixture.order(issued)
      val reference = ContextReference("external-review-result", fixture.originalCandidate)
      WorkflowWorkerMapping.normalizeC(dispatch, SkillWorkResult(
        request.runId, request.continuationId, request.expectedRevision, request.context.snapshot,
        TypedValue("ReviewStepResult", value), reference, Vector.empty, Vector.empty,
        Some(worker), Some(observedModel), profile.identity, policy.version))
    })

  def submitC(result: ContinuationResult[ReviewStepResult]): Consequence[String] =
    WorkflowResultJsonV1.encodeC(result, GoalPhaseReviewCompletionOperation.payloadCodec)
      .flatMap(wire => fixture.invoke("submitStepReview", wire))

  def close(): Unit = fixture.subsystem.shutdown()
}

private[admission] object GoalPhaseWorkerFixtureHost {
  val program = "object Increment { def apply(value: Int): Int = value + 1 }\n"
  val planning = "Implement Increment.apply: increment a Scala Int by one using normal Int overflow semantics.\n" +
    "The complete Step consists only of this function and this planning record; no I/O, external commands or publication.\n"

  def parseC(wire: String): Consequence[Json] =
    parse(wire).fold(_ => Consequence.stateConflict("Fixture JSON is invalid"), Consequence.success)

  /** Fixed known-candidate Provider, not a replay or copy of a Codex answer. */
  def deterministicC(fixture: GoalPhaseApplicationFixture): Consequence[ReviewStepResult] =
    if (new String(fixture.program.toArray, StandardCharsets.UTF_8) != program ||
        new String(fixture.planning.toArray, StandardCharsets.UTF_8) != planning)
      Consequence.stateConflict("Deterministic Provider has no calibration for this candidate")
    else Consequence.success(ReviewStepResult("accept", "Exact known increment candidate satisfies its complete Step contract",
      Vector(StepReviewEvidence("deterministic-review", fixture.originalCandidate,
        "2026-09-27T23:59:00Z", "full-step",
        "Fixed known-candidate verification", "deterministic-provider"))))

  def trace(fixture: GoalPhaseApplicationFixture): Json = Json.arr(fixture.stored.history.map(entry => Json.obj(
    "revision" -> Json.fromLong(entry.revision.value),
    "lifecycle" -> Json.fromString(entry.lifecycle.toString),
    "progression" -> entry.currentProgression.map(x => Json.fromString(x.value)).getOrElse(Json.Null)
  ))*)
}

/** Private fixture transport. A unique parent-allocated directory removes any
  * dependence on the execution runner forwarding interactive agent messages.
  * The completion-ready marker is published only after completion.json is whole.
  */
private[admission] object GoalPhaseWorkerFixtureTransport {
  def open(path: String): Path = {
    val directory = Path.of(path)
    require(directory.isAbsolute && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS), "Expected a private fixture directory")
    require(Vector("issued.json", "completion.json", "completion-ready", "proof.json", "failure.json")
      .forall(name => !Files.exists(directory.resolve(name), LinkOption.NOFOLLOW_LINKS)), "Fixture transport must be fresh")
    directory
  }

  def emit(directory: Path, name: String, wire: String): Unit = {
    require(Set("issued.json", "proof.json", "failure.json").contains(name), "Unknown fixture output")
    Files.writeString(directory.resolve(name), wire + "\n", StandardCharsets.UTF_8,
      StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
  }

  def awaitCompletion(directory: Path, timeoutMillis: Long = 540000L): String = {
    val deadline = System.nanoTime() + timeoutMillis * 1000000L
    val marker = directory.resolve("completion-ready")
    while (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS) && System.nanoTime() < deadline)
      Thread.sleep(50L)
    require(Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS), "Fixture completion timed out or marker is not a regular file")
    val completion = directory.resolve("completion.json")
    require(Files.isRegularFile(completion, LinkOption.NOFOLLOW_LINKS), "Fixture completion must be a regular file")
    Files.readString(completion, StandardCharsets.UTF_8).stripSuffix("\n")
  }
}

/** Keeps both instances live until one Host-attested JSON completion arrives.
  * stdout records retain the exact common envelopes for live proof and replay.
  */
object GoalPhaseWorkerFixtureMain {
  def main(args: Array[String]): Unit = {
    import GoalPhaseApplicationFixture.take
    import GoalPhaseWorkerFixtureHost.*
    require((args.length == 1 || args.length == 2) && args(0).trim.nonEmpty, "Expected the selected model identity and optional private transport directory")
    val directory = if (args.length == 2) Some(GoalPhaseWorkerFixtureTransport.open(args(1))) else None
    val host = new GoalPhaseWorkerFixtureHost(args(0))
    val provider = new GoalPhaseApplicationFixture(viaCli = true, fromPublicStart = true,
      withAuthority = true, programText = program, planningText = planning)
    try {
      println(host.issuedJson.noSpaces)
      directory.foreach(path => GoalPhaseWorkerFixtureTransport.emit(path, "issued.json", host.issuedJson.noSpaces))
      val input = directory.map(GoalPhaseWorkerFixtureTransport.awaitCompletion(_)).getOrElse(
        Option(scala.io.StdIn.readLine()).getOrElse(throw new IllegalStateException("No external worker completion")))
      val json = take(parseC(input))
      require(json.asObject.exists(_.keys.toSet == Set("workerIdentity", "modelIdentity", "result")), "Invalid Host completion shape")
      val worker = json.hcursor.get[String]("workerIdentity").fold(message => throw new IllegalStateException(message.toString), identity)
      val model = json.hcursor.get[String]("modelIdentity").fold(message => throw new IllegalStateException(message.toString), identity)
      val payload = json.hcursor.downField("result").focus.get
      val normalized = take(host.normalizeC(worker, model, payload))
      val liveWire = take(host.submitC(normalized))
      val live = take(WorkflowDecisionJsonV1.decodeC(liveWire, GoalPhaseClosureAuthority.inputCodec))
      require(live.handle == host.started.handle && host.fixture.saved.submitted == normalized, "Worker changed the Start instance or Result")

      val providerStart = take(provider.start())
      provider.associateClose(providerStart.handle)
      val providerOrder = provider.firstReview()
      val value = take(deterministicC(provider))
      val deterministic = provider.result(providerOrder, value, "ReviewStepResult",
        ContextReference("deterministic-review-result", provider.originalCandidate))
      val providerWire = take(provider.invoke("submitStepReview", provider.reviewWire(deterministic)))
      val providerDecision = take(WorkflowDecisionJsonV1.decodeC(providerWire, GoalPhaseClosureAuthority.inputCodec))
      require(providerDecision.handle == providerStart.handle, "Provider changed its Start instance")
      require(normalized.result.value.decision == value.decision && host.started.handle.workflowIdentity == providerStart.handle.workflowIdentity &&
        host.started.handle.workflowRevision == providerStart.handle.workflowRevision && trace(host.fixture) == trace(provider), "Provider replacement changed judgment or Workflow transitions")
      require(host.fixture.commits.isEmpty && provider.commits.isEmpty, "Missing authority must not commit")
      require(deterministic.evidence.workerIdentity.isEmpty && deterministic.evidence.modelIdentity.isEmpty && deterministic.evidence.skillDispatch.isEmpty,
        "Deterministic Provider invented AI execution metadata")
      val proof = Json.obj("kind" -> Json.fromString("completed-worker-proof"),
        "issued" -> host.issuedJson, "hostCompletion" -> json,
        "normalizedResult" -> take(parseC(host.fixture.reviewWire(normalized))),
        "providerResult" -> take(parseC(provider.reviewWire(deterministic))),
        "liveOutcome" -> take(parseC(liveWire)), "providerOutcome" -> take(parseC(providerWire)),
        "liveTrace" -> trace(host.fixture), "providerTrace" -> trace(provider),
        "sameStartInstance" -> Json.True, "providerTransitionParity" -> Json.True,
        "realGitMutation" -> Json.False).noSpaces
      println(proof)
      directory.foreach(path => GoalPhaseWorkerFixtureTransport.emit(path, "proof.json", proof))
    } catch {
      case failure: Exception =>
        directory.foreach(path => GoalPhaseWorkerFixtureTransport.emit(path, "failure.json",
          Json.obj("kind" -> Json.fromString("worker-proof-failed"),
            "message" -> Json.fromString(Option(failure.getMessage).getOrElse(failure.getClass.getName))).noSpaces))
        throw failure
    } finally {
      provider.subsystem.shutdown()
      host.close()
    }
  }
}
