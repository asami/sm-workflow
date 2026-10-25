package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import WorkflowExecutionHarness as H

/** Normal configured Operations and specification-owned executor observations.
  * These examples establish no installed provider, real task or two-chat claim.
  * @since   Oct. 16, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeCallerExecutionOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationOperationFixture {
  import NativeCallerExecutionFixture.*

  private def _state(result: Consequence[OperationResponse]): V.WorkflowCallerExecutionState = take(result) match {
    case OperationResponse.Json(value) => take(summon[ValueReader[V.WorkflowCallerExecutionState]].readC(record(value)))
    case other => fail("Expected direct normal structured execution state: " + other)
  }
  private def _prepare(episode: PreparationEpisode, context: V.WorkflowIssuedWorkContext,
    alternate: Boolean = true): V.WorkflowCallerExecutionState = _state(episode.invoke("prepareExecution",
    V.WorkflowCallerExecutionPrepareInput(context, episode.save("mapping", mapping(alternate).noSpaces),
      episode.save("origin", origin.noSpaces)).toRecord()))
  private def _input(episode: PreparationEpisode, context: V.WorkflowIssuedWorkContext,
    state: V.WorkflowCallerExecutionState, observation: Json, reference: Option[String] = None): V.WorkflowCallerExecutionRecordInput =
    V.WorkflowCallerExecutionRecordInput(context, episode.save("session", state.session),
      episode.save("observation", observation.noSpaces), reference)
  private def _record(episode: PreparationEpisode, context: V.WorkflowIssuedWorkContext,
    state: V.WorkflowCallerExecutionState, status: String, reason: String,
    reference: Boolean = false): V.WorkflowCallerExecutionState = _state(episode.invoke("recordExecution",
    _input(episode, context, state, observation(state, status, reason),
      Option.when(reference)(episode.saveValue("reference", artifact))).toRecord()))

  "Normal passive host execution Operations" should afterWord(
    "in spec:native-host-execution, examples:N1,N2,N3,N4, rules:R6,R7,R8,R9, phase:6") {
    "N1 prepare all four actual public scopes with declared and normal wire requirements" in {
      withPreparation() { episode =>
        for (mode <- NativeWorkflowIssuedWorkContext.Mode.values.toVector; wire <- Vector(false, true)) {
          Given("R6,R7: the actual generated " + mode.value + " scope, original mapping and current origin files; wire=" + wire)
          val context = issued(mode, wire = wire)
          When("the exact configured normal prepareExecution Operation reads its selected files")
          val state = _prepare(episode, context)
          val session = take(H.decodeC(state.session))
          Then("the direct generated response retains an opaque canonical Session/selection and produces no completion")
          session.workReference.runId.value shouldBe context.runId
          session.requirement.reasoningClass.get.value shouldBe "FixtureClass"
          session.nextSelection shouldBe Some(H.Selection(1, primary))
          state.nextSelection shouldBe Some(take(H.encodeSelectionC(session.nextSelection.get)))
          state.completionEvidence shouldBe None
          session.primary.controls shouldBe primary.controls
        }
      }
    }

    "N2 restore the supplied selection after unavailable or declined and complete only the configured alternate" in {
      withPreparation() { episode =>
        for ((status, reason) <- Vector("UNAVAILABLE" -> "ProviderUnavailable", "DECLINED" -> "CapabilityDeclined")) {
          Given("R7,R8: an issued Request with a retained Session and an actual specification-owned " + status + " observation")
          val context = issued(wire = true)
          val initial = _prepare(episode, context)
          When("recordExecution consumes noncompletion and a later call consumes the saved alternate with one genuine reference")
          val fallback = _record(episode, context, initial, status, reason)
          val completed = _record(episode, context, fallback, "COMPLETED", "Completed", reference = true)
          val restored = take(H.decodeC(completed.session))
          Then("the original work reference and finite sequence survive without another mapping read and evidence uses the actual executor")
          fallback.completionEvidence shouldBe None
          take(H.decodeC(fallback.session)).nextSelection shouldBe Some(H.Selection(2, alternate))
          restored.workReference shouldBe take(H.decodeC(initial.session)).workReference
          restored.attempts.map(_.outcome.status.value) shouldBe Vector(status, "COMPLETED")
          completed.nextSelection shouldBe None
          val evidence = completed.completionEvidence.get
          evidence.references shouldBe Vector(artifact)
          evidence.workerIdentity shouldBe Some("fixture-independent")
          evidence.modelIdentity shouldBe Some(alternate.model)
          evidence.execution.get.profileIdentity shouldBe alternate.identity
          restored.attempts.last.outcome.resources.inputTokens shouldBe None
        }
      }
    }

    "N3 keep blocked failed uncertain exhausted and inline outcomes outside completion and reject consumed contradictions" in {
      withPreparation() { episode =>
        for ((status, reason) <- Vector("BLOCKED" -> "AuthorityBlocked", "BLOCKED" -> "ResourceBlocked",
            "FAILED" -> "SemanticFailure", "FAILED" -> "UncertainExecution")) {
          Given("R8: an actual pending delegated Session and terminal " + reason + " observation")
          val context = issued()
          val initial = _prepare(episode, context)
          When("one normal recordExecution observes that outcome")
          val stopped = _record(episode, context, initial, status, reason)
          Then("no completion or hidden alternate is emitted")
          stopped.nextSelection shouldBe None
          stopped.completionEvidence shouldBe None
        }
        for (inline <- Vector(false, true)) {
          Given("R8: an INLINE or no-alternate Session with an unavailable selected executor")
          val context = issued(inline = inline)
          val initial = _prepare(episode, context, alternate = false)
          When("normal recordExecution consumes unavailability")
          val stopped = _record(episode, context, initial, "UNAVAILABLE", "ProviderUnavailable")
          Then("selection is exhausted without completion")
          stopped.nextSelection shouldBe None
          stopped.completionEvidence shouldBe None
        }
        Given("R7,R8: a retained REQUIRED-review issue and supplied stale, mismatched or contradictory observations")
        val context = issued()
        val initial = _prepare(episode, context)
        val fallback = _record(episode, context, initial, "UNAVAILABLE", "ProviderUnavailable")
        val exhausted = _record(episode, context, fallback, "DECLINED", "CapabilityDeclined")
        val reference = Some(episode.saveValue("required-reference", artifact))
        val complete = observation(initial, "COMPLETED", "Completed")
        val same = complete.mapObject(_.add("executor", executor(primary, "fixture-origin")))
        val wrong = complete.mapObject(_.add("executor", executor(alternate, "fixture-independent")))
        val mismatch = context.copy(runId = "other-run")
        val contradictory = context.copy(executionRequirement = record(json(context.executionRequirement)
          .mapObject(_.add("review_required", Json.False))))
        When("the normal Operations consume crossed references, alias conflict, stale selection and invalid actual descriptors")
        val refused = Vector(
          episode.invoke("prepareExecution", V.WorkflowCallerExecutionPrepareInput(contradictory,
            episode.save("contradictory-mapping", mapping().noSpaces), episode.save("contradictory-origin", origin.noSpaces)).toRecord()),
          episode.invoke("recordExecution", _input(episode, mismatch, initial, complete, reference).toRecord()),
          episode.invoke("recordExecution", _input(episode, context, fallback, complete, reference).toRecord()),
          episode.invoke("recordExecution", _input(episode, context, initial, same, reference).toRecord()),
          episode.invoke("recordExecution", _input(episode, context, initial, wrong, reference).toRecord()),
          episode.invoke("recordExecution", _input(episode, context, exhausted,
            observation(fallback, "UNAVAILABLE", "ProviderUnavailable")).toRecord()))
        Then("all contradictions refuse and the exhausted alternate yields no evidence or next attempt")
        refused.foreach(_ shouldBe a[Consequence.Failure[?]])
        exhausted.nextSelection shouldBe None
        exhausted.completionEvidence shouldBe None
      }
    }

    "N4 refuse invalid URI/reference metadata and current normal reader escape or byte-cap violations" in {
      withPreparation(cap = 4096) { episode =>
        Given("R9: a bounded normal current reader, selected canonical inputs and a pending Session")
        val context = issued()
        val initial = _prepare(episode, context)
        val session = episode.save("metadata-session", initial.session)
        val completed = episode.save("metadata-completed", observation(initial, "COMPLETED", "Completed").noSpaces)
        val unavailable = episode.save("metadata-unavailable", observation(initial, "UNAVAILABLE", "ProviderUnavailable").noSpaces)
        val reference = episode.saveValue("metadata-reference", artifact)
        val malformed = episode.save("bad-reference", "{\"identity\":\"fixture\"}")
        val outside = Files.createTempFile("smwf-outside-execution-", ".json")
        try {
          Files.writeString(outside, mapping().noSpaces)
          val oversized = episode.save("oversized-mapping", " " * 4097)
          When("the actual normal Operations receive invalid required/optional URIs, missing or noncompletion references, or denied resources")
          val inputs = Vector(
            V.WorkflowCallerExecutionRecordInput(context, "relative.json", completed, Some(reference)),
            V.WorkflowCallerExecutionRecordInput(context, session, "https://example.invalid/observation", Some(reference)),
            V.WorkflowCallerExecutionRecordInput(context, session, completed, Some("file:relative.json")),
            V.WorkflowCallerExecutionRecordInput(context, session, completed, None),
            V.WorkflowCallerExecutionRecordInput(context, session, completed, Some(malformed)),
            V.WorkflowCallerExecutionRecordInput(context, session, unavailable, Some(reference)))
          val refused = inputs.map(input => episode.invoke("recordExecution", input.toRecord())) ++
            Vector(outside.toUri.toASCIIString, oversized).map(path => episode.invoke("prepareExecution",
              V.WorkflowCallerExecutionPrepareInput(context, path, episode.save("bounded-origin", origin.noSpaces)).toRecord()))
          val required = episode.invoke("prepareExecution", Record.data("issuedWorkContext" -> context.toRecord(), "mapping" -> reference))
          Then("normal errors return no successful execution state, including missing required generated metadata")
          (refused :+ required).foreach(_ shouldBe a[Consequence.Failure[?]])
        } finally Files.deleteIfExists(outside)
      }
    }
  }
}

/** Shared scenario data for normal Operation and separate command examples.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object NativeCallerExecutionFixture {
  val primary = WorkflowConcreteExecutionProfile("fixture-primary", "fixture-provider", "fixture-model", Map("reasoningEffort" -> "bounded"))
  val alternate = primary.copy(identity = "fixture-alternate", model = "fixture-alternate-model")
  val artifact = V.WorkflowArtifactReference("fixture-semantic-result", "1")
  def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => throw new AssertionError("Normal execution specification failed: " + other)
  }
  def record(value: Json): Record = take(new RecordDecoder().json(value.noSpaces))
  def json(value: Record): Json = io.circe.parser.parse(take(value.toJsonStringC)).toOption.get
  def profile(value: WorkflowConcreteExecutionProfile): Json = Json.obj("identity" -> Json.fromString(value.identity),
    "provider" -> Json.fromString(value.provider), "model" -> Json.fromString(value.model),
    "controls" -> Json.obj(value.controls.toVector.map((key, text) => key -> Json.fromString(text))*))
  def executor(value: WorkflowConcreteExecutionProfile, context: String): Json = Json.obj(
    "contextIdentity" -> Json.fromString(context), "profile" -> profile(value))
  def mapping(alternate: Boolean = true, revision: String = "1"): Json = {
    val classes = Vector("FixtureClass", "CodingProgrammingStandard", "ReviewStandard")
    def _bindings_(identity: String): Json = Json.fromValues(classes.map(name => Json.obj(
      "reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(identity))))
    Json.obj("schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
      "identity" -> Json.fromString("fixture-mapping"), "revision" -> Json.fromString(revision),
      "profiles" -> Json.arr(profile(primary), profile(NativeCallerExecutionFixture.alternate)),
      "classes" -> _bindings_(primary.identity),
      "fallbacks" -> (if (alternate) _bindings_(NativeCallerExecutionFixture.alternate.identity) else Json.arr()))
  }
  def origin: Json = Json.obj("schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
    "current" -> executor(primary, "fixture-origin"),
    "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.True))
  def observation(state: V.WorkflowCallerExecutionState, status: String, reason: String): Json = {
    val selection = io.circe.parser.parse(state.nextSelection.get).toOption.get
    selection.mapObject(_.add("status", Json.fromString(status)).add("reason", Json.fromString(reason))
      .add("detail", Json.fromString("Observed specification-owned " + status)).add("resources", Json.obj())
      .add("executor", if (status == "COMPLETED") Json.obj("contextIdentity" -> Json.fromString("fixture-independent"),
        "profile" -> selection.hcursor.downField("profile").focus.get) else Json.Null))
  }
  def issued(mode: NativeWorkflowIssuedWorkContext.Mode = NativeWorkflowIssuedWorkContext.Mode.Implementation,
    inline: Boolean = false, wire: Boolean = false): V.WorkflowIssuedWorkContext = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("fixture-workflow"),
      WorkflowDefinitionRevision("1"), InstanceIdentity("fixture-instance"))
    val context = ContextBundle("Assigned actual public scope", Vector.empty, Vector.empty,
      ContextSnapshot(handle.workflowRevision.value, None, None, None))
    val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
      val typeIdentity = "FixtureSemanticInput"
      def encode(value: String): Json = Json.fromString(value)
      def decode(value: Json): Either[String, String] = value.asString.toRight("Expected fixture text")
    }
    val request = ContinuationRequest(StateMachineRunIdentity("fixture-run"), ContinuationIdentity("fixture-next"),
      StateMachineRevision("7"), StateMachineRequiredOperationIdentity("fixture-work"), StateMachineOperationIdentity("Fixture", "work"),
      Some(TypedValue(codec.typeIdentity, "scope")), Some(StateMachineResultTypeReference("FixtureResult")), context,
      CompletionContract("fixture-completion", Vector.empty), EvidenceContract("fixture-evidence", Vector.empty),
      StateMachineOperationIdentity("Fixture", "submit"))
    val requirement = ExecutionRequirement(Vector(CapabilityRequirement("fixture-work")), RiskLevel("bounded"), ReasoningLevel.Standard,
      !inline, Some(ExecutionDisposition(if (inline) ExecutionPlacement.Inline else ExecutionPlacement.Delegated,
        if (inline) ExecutionIndependence.Optional else ExecutionIndependence.Required, "fixture-origin")),
      Some(ReasoningClassReference("FixtureClass")))
    val work = WorkflowInteraction[String, Nothing](handle, WorkflowContinuation.WorkOrder(request, requirement,
      MinimalPresentation("Assigned fixture work", "Pending")))
    val result = take(NativeWorkflowIssuedWorkContext.fromIssuedC(work, mode, codec))
    if (!wire) result else result.copy(executionRequirement = record(json(result.executionRequirement).mapObject(fields =>
      fields.remove("reviewRequired").remove("reasoningClass").add("review_required", fields("reviewRequired").get)
        .add("reasoning_class", fields("reasoningClass").get).add("disposition", fields("disposition").get.mapObject(nested =>
          nested.remove("originContextIdentity").add("origin_context_identity", nested("originContextIdentity").get))))))
  }
}
