package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.Instant
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import NativeHostEvidence.*
import SplitPhaseNativeHumanDirection as Scope
import SplitPhaseReasoningHumanDirection as Reasoning
import SplitPhaseHumanDirectionAnswerContract as A
import SplitPhaseHumanAnswerVerification.AnswerContext
import SplitPhaseExplicitPartitionFixture.take

/** Scope-only passive fixture; deliberately inert frontier/closure display is
  * not a public input, native history, consent enrollment or execution permit.
  * Normal managed Operation examples exercise genuinely issued questions.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
private[admission] object NativeHostSplitAnswerEvidenceFixture {
  val owner = ComponentInstanceId(SmWorkflowComponent.componentId, "split-answer-spec")
  val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("SplitPhaseWorkflow"),
    WorkflowDefinitionRevision("actual-executable"), InstanceIdentity("instance"))
  def ref(identity: String, revision: String = "1"): Json = Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
  val context = SplitPhaseExplicitPartitionSemantic.ContextReference("original-context", "1")
  val scope = Scope.Input(context, SplitPhasePlanningCheckpointPreparation.Reference("planning-checkpoint", "1"),
    SplitPhaseScopeProjectionCheckpointPreparation.Reference("scope-checkpoint", "1"), Scope.FrontierPayload("unusable-independent-frontier-copy"))
  val model = ContextSnapshot(handle.workflowRevision.value, modelRevision = Some("actual-model"))
  private val workTarget = Json.obj("context" -> ref(context.identity), "source" -> ref("source"), "authority" -> ref("authority"),
    "demand" -> ref("demands"), "items" -> Json.arr(Json.obj("identity" -> Json.obj("value" -> Json.fromString("unknown-item")),
      "reason" -> Json.fromString("Original explanation"), "closure" -> Json.fromString("unused-closure-copy"))), "result" -> ref("reasoning-result"))
  val work = Json.obj("schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion), "kind" -> Json.fromString("WORK_ORDER"),
    "handle" -> NativeHostHumanScope.handleJson(handle), "request" -> Json.obj("runId" -> Json.fromString("instance"),
      "continuationId" -> Json.fromString("reasoning-slot"), "expectedRevision" -> Json.fromString("3"),
      "resultType" -> Json.fromString(SplitPhaseExplicitReasoningSemanticJson.resultCodec.typeIdentity),
      "context" -> Json.obj("snapshot" -> Json.obj("workflowRevision" -> Json.fromString(model.workflowRevision), "modelRevision" -> Json.fromString("actual-model"))),
      "input" -> Json.obj("typeIdentity" -> Json.fromString(SplitPhaseExplicitReasoningSemanticJson.requestCodec.typeIdentity),
        "value" -> Json.obj("schemaVersion" -> Json.fromString(SplitPhaseExplicitReasoningSemanticJson.requestCodec.schema), "payload" -> workTarget))))
  val reasoningResult = Json.obj("schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion), "kind" -> Json.fromString("CONTINUATION_RESULT"),
    "handle" -> NativeHostHumanScope.handleJson(handle), "runId" -> Json.fromString("instance"), "continuationId" -> Json.fromString("reasoning-slot"),
    "expectedRevision" -> Json.fromString("3"), "contextSnapshot" -> Json.obj("workflowRevision" -> Json.fromString(model.workflowRevision), "modelRevision" -> Json.fromString("actual-model")),
    "result" -> Json.obj("reference" -> ref("reasoning-result"), "typeIdentity" -> Json.fromString(SplitPhaseExplicitReasoningSemanticJson.resultCodec.typeIdentity),
      "value" -> Json.fromString("unused-completed-result-body-copy")))
  val reasoning = Reasoning.Input(context, work.noSpaces, reasoningResult.noSpaces, "unusable-independent-frontier-copy")
  private def question[W](input: W, typeIdentity: String) = {
    val request = ContinuationRequest(StateMachineRunIdentity("instance"), ContinuationIdentity("split-human-slot"), StateMachineRevision("5"),
      StateMachineRequiredOperationIdentity("actual-human-capability"), StateMachineOperationIdentity("HumanService", "answer"),
      Some(TypedValue(typeIdentity, input)), Some(StateMachineResultTypeReference(A.resultType)),
      ContextBundle("Original question context", Vector.empty, Vector.empty, model), CompletionContract("actual-completion", Vector.empty),
      EvidenceContract("actual-evidence", Vector.empty), Scope.completionOperation)
    WorkflowInteraction[W, Nothing](handle, WorkflowContinuation.Decision(request, MinimalPresentation("Original title", "Original notice")))
  }
  val scopeQuestion = question(scope, Scope.inputCodec.typeIdentity)
  val reasoningQuestion = question(reasoning, Reasoning.inputCodec.typeIdentity)
  def answer(question: WorkflowInteraction[?, Nothing], direction: A.Answer = A.Answer.ProvideResolution(
    SplitPhaseExplicitPartitionSemantic.ContextReference("actual-resolution", "1"))) = {
    val q = question.current.asInstanceOf[WorkflowContinuation.Decision[?]].request
    ContinuationResult(question.handle, q.runId, q.continuationId, q.expectedRevision, q.context.snapshot, TypedValue(A.resultType, direction),
      ContextReference("human-answer", "1"), Vector.empty, ExecutionEvidence(Vector.empty, Some("executing-relay")))
  }
  def production(reasoning: Boolean): Json = {
    val q = if (reasoning) reasoningQuestion else scopeQuestion
    take(ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(AnswerContext(owner, q, answer(q), "executing-relay")))
  }
  def raw(reasoning: Boolean): Json = {
    val q = if (reasoning) reasoningQuestion else scopeQuestion
    val selected = production(reasoning)
    val target = at(selected, "splitAnswerAttestation", "question")
    val input = if (reasoning) Json.obj("schemaVersion" -> Json.fromString(Reasoning.schema), "payload" -> Json.obj(
      "context" -> ref(context.identity), "reasoningWork" -> Json.fromString(work.noSpaces), "reasoningResult" -> Json.fromString(reasoningResult.noSpaces),
      "frontier" -> Json.fromString("unused-frontier-copy"))) else Json.obj("schemaVersion" -> Json.fromString(Scope.inputSchema), "payload" -> Json.obj(
        "context" -> ref(context.identity), "checkpoint" -> ref("planning-checkpoint"), "scopeCheckpoint" -> ref("scope-checkpoint"),
        "frontier" -> Json.obj("value" -> Json.fromString("unused-frontier-copy"))))
    val question = Json.obj("schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion), "kind" -> Json.fromString("DECISION"),
      "handle" -> at(target, "handle"), "request" -> target.mapObject(o => o.remove("handle").remove("source").remove("modelRevision").remove("inputType")
        .add("context", Json.obj("snapshot" -> Json.obj("workflowRevision" -> Json.fromString(model.workflowRevision), "modelRevision" -> Json.fromString("actual-model")),
          "purpose" -> Json.fromString("Original question context"))).add("input", Json.obj("typeIdentity" -> at(target, "inputType"), "value" -> input))),
      "presentation" -> Json.fromString("unused-presentation-copy"))
    val result = io.circe.parser.parse(take(WorkflowResultJsonV1.encodeC(answer(q), A.payloadCodec))).toOption.get
    Json.obj("componentInstance" -> Json.fromString(owner.canonicalKey), "submittingParticipant" -> Json.fromString("executing-relay"),
      "question" -> question, "result" -> result)
  }
  def at(value: Json, keys: String*): Json = keys.foldLeft(value)((v, k) => v.hcursor.downField(k).focus.get)
  def alter(value: Json, path: Vector[String])(f: Json => Json): Json = path.headOption match {
    case None => f(value)
    case Some(key) => value.mapObject(o => o.add(key, alter(o(key).get, path.tail)(f)))
  }
}

final class NativeHostSplitAnswerScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeHostSplitAnswerEvidenceFixture.*
  private val now = Instant.parse("2026-10-08T00:00:00Z")
  private val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("test", "host", "split_human")), "independent-host")
  private val lookup = ThreeWorkflowHostEvidencePorts.key("human-answer", "1")
  private def evidence(binding: Json) = Evidence(schema, Purpose.SplitAnswer.identity, lookup, "human-answer", "1", declared.issuer,
    "independent-human-relay", binding, now.minusSeconds(60).toString)
  private def observe(binding: Json, target: Json) = observeC(evidence(binding), declared, Purpose.SplitAnswer, lookup, target, now)

  "Independent Split Human applicability" should {
    "attest actual Scope and Reasoning targets without retaining frontier question or result copies" in {
      Given("Spec: docs/spec/native-host-split-answer-applicability.md; Rule: R1; Example: E1; passive target fixtures, not valid public frontiers or evidence issuance")
      When("production and historical evidence select the same actual target and direction")
      val cases = Vector(false, true).map(reasoning => (production(reasoning), NativeHostSplitAnswerScope.select(Purpose.SplitAnswer, raw(reasoning), legacy = true)))
      Then("selected target/direction survive without body copies, and Scope cannot be substituted for Reasoning")
      cases.foreach { (selected, legacy) =>
        legacy shouldBe Right(selected)
        selected.noSpaces should not include "frontier"
        selected.noSpaces should not include "reasoningWork"
        selected.noSpaces should not include "closure"
        selected.noSpaces should not include "presentation"
      }
      observe(production(false), production(true)).isSuccess shouldBe false
    }
    "ignore unused historical display frontier closure and completed-result-body copies" in {
      Given("Spec: docs/spec/native-host-split-answer-applicability.md; Rule: R1; Example: E2; original selected meaning with obsolete copied diagnostics")
      When("historical scope selection discards the unused witnesses, including differently formatted reasoning transports")
      Vector(false, true).foreach { reasoning =>
        val changed = alter(raw(reasoning), Vector("question", "request", "input", "value", "payload")) { payload =>
          payload.mapObject(o => {
            val ignored = o.add("frontier", Json.Null).add("sourceLine", Json.fromInt(99999)).add("generator", Json.True)
            if (reasoning) ignored.add("reasoningWork", Json.fromString(alter(work, Vector("request", "input", "value", "payload", "items"))(
              _.mapArray(_.map(_.mapObject(_.add("closure", Json.Null).add("reason", Json.True))))).spaces2))
              .add("reasoningResult", Json.fromString(alter(reasoningResult, Vector("result"))(_.mapObject(_.add("value", Json.Null))).spaces2))
            else ignored
          })
        }
        val diagnostic = alter(changed, Vector("question", "request", "context"))(_.mapObject(_.add("purpose", Json.Null)))
        Then("scope and original independent Human occurrence remain usable without renewal or public-input authorization")
        val observed = observe(diagnostic, production(reasoning)).toOption.get
        observed.evidence.binding shouldBe production(reasoning)
        observed.evidence.participant shouldBe "independent-human-relay"
        observed.occurredAt shouldBe now.minusSeconds(60)
      }
    }
    "refuse actual question slot model source assignment choice resolution or issuer substitution" in {
      Given("Spec: docs/spec/native-host-split-answer-applicability.md; Rule: R2; Example: E3; independent consent to the actual direction")
      When("actual selected scope is changed rather than explanatory copies")
      Vector(false, true).foreach { reasoning =>
        val target = production(reasoning)
        val variants = Vector(
          target.mapObject(_.add("componentInstance", Json.fromString("foreign-owner"))),
          alter(target, Vector("splitAnswerAttestation"))(_.mapObject(_.add("submittingParticipant", Json.fromString("foreign-relay")))),
          alter(target, Vector("splitAnswerAttestation", "question"))(_.mapObject(_.add("continuationId", Json.fromString("foreign-slot")))),
          alter(target, Vector("splitAnswerAttestation", "question"))(_.mapObject(_.add("modelRevision", Json.fromString("foreign-model")))),
          alter(target, Vector("splitAnswerAttestation", "question", "source", "context"))(_.mapObject(_.add("identity", Json.fromString("foreign-context")))),
          alter(target, Vector("splitAnswerAttestation", "answer"))(_.mapObject(_.add("choice", Json.fromString("Cancel")).add("resolution", Json.Null))),
          alter(target, Vector("splitAnswerAttestation", "answer", "resolution"))(_.mapObject(_.add("revision", Json.fromString("foreign-resolution")))))
        Then("every actual substitution is rejected; independent issuer and non-future occurrence remain required")
        variants.foreach(v => observe(v, target).isSuccess shouldBe false)
        Vector(evidence(target).copy(reference = "foreign-answer"), evidence(target).copy(issuer = "foreign-issuer"),
          evidence(target).copy(occurredAt = now.plusSeconds(1).toString)).foreach(v =>
          observeC(v, declared, Purpose.SplitAnswer, lookup, target, now).isSuccess shouldBe false)
      }
      val assignment = alter(production(true), Vector("splitAnswerAttestation", "question", "source", "work"))(
        _.mapObject(_.add("items", Json.arr(Json.fromString("another-unknown-item")))))
      observe(assignment, production(true)).isSuccess shouldBe false
    }
    "load v1 through v6 without copied-body proof and write only closed v7 scope" in {
      Given("Spec: docs/spec/native-host-split-answer-applicability.md; Rule: R3; Example: E4; original independently provisioned historical records")
      When("recognized old storage is read without row rewrite or renewed consent")
      Vector(false, true).foreach { reasoning =>
        val current = encodeStorageC(evidence(raw(reasoning))).toOption.get
        val payload = io.circe.parser.parse(current.substring(schema.length + 1)).toOption.get
        Vector("v1", "v2", "v3", "v4", "v5", "v6").foreach { v =>
          val version = "sm-workflow.native-host-evidence." + v
          val old = payload.mapObject(_.add("schemaVersion", Json.fromString(version)).add("binding", raw(reasoning)))
          Then("original actual meaning and time survive, while current storage cannot restore copies or grants")
          decodeStorageC(version + ":" + old.noSpaces).toOption.get shouldBe evidence(production(reasoning))
        }
        decodeStorageC(current).toOption.get shouldBe evidence(production(reasoning))
        decodeStorageC(schema + ":" + payload.mapObject(_.add("binding", raw(reasoning))).noSpaces).isSuccess shouldBe false
        Vector("frontier", "sourceLine", "generator", "commandGrant").foreach { field =>
          val extended = alter(production(reasoning), Vector("splitAnswerAttestation", "question"))(_.mapObject(_.add(field, Json.True)))
          decodeStorageC(schema + ":" + payload.mapObject(_.add("binding", extended)).noSpaces).isSuccess shouldBe false
        }
      }
    }
    "preserve all three choices and reject contradictory actual source or answer correlation" in {
      Given("Spec: docs/spec/native-host-split-answer-applicability.md; Rule: R2; Example: E5; actual direction rather than whole Result equality")
      When("direct producers receive genuine target variants or foreign slots, types and selected reasoning result")
      Vector(scopeQuestion, reasoningQuestion).foreach { q =>
        val stopped = Vector(A.Answer.Defer, A.Answer.Cancel).map(a => ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(AnswerContext(owner, q, answer(q, a), "executing-relay")))
        Then("stopped choices remain distinct without a resolution, while actual correlation conflicts fail")
        stopped.foreach(_.isSuccess shouldBe true)
        stopped.head.toOption.get should not be stopped.last.toOption.get
        ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(AnswerContext(owner, q,
          answer(q).copy(continuationId = ContinuationIdentity("foreign-slot")), "executing-relay")).isSuccess shouldBe false
      }
      val crossed = alter(raw(true), Vector("question", "request", "input", "value", "payload"))(_.mapObject(_.add("reasoningResult",
        Json.fromString(alter(reasoningResult, Vector("result", "reference"))(_.mapObject(_.add("identity", Json.fromString("foreign-result")))).noSpaces))))
      NativeHostSplitAnswerScope.select(Purpose.SplitAnswer, crossed, legacy = true).isRight shouldBe false
      val foreign = alter(raw(false), Vector("result"))(_.mapObject(_.add("expectedRevision", Json.fromString("foreign-revision"))))
      NativeHostSplitAnswerScope.select(Purpose.SplitAnswer, foreign, legacy = true).isRight shouldBe false
    }
  }
}
