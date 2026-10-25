package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextReference as FrameworkReference, *}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitReasoningSemantic as R,
  SplitPhaseExplicitReasoningSemanticJson as J, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitBoundaryValidation as B,
  SplitPhaseExplicitObjectiveSelection as O}
import SplitPhaseExplicitPartitionFixture.take

/*
 * @since   Oct.  5, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseExplicitReasoningSemanticJsonSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _assigned_reply_spec = afterWord("in spec:split-phase-reasoning-interface, example:E2, rules:R2, phase:2")
  private val _authority = P.AuthorityReference("original-authority", "1")
  private val _request = R.Request(S.ContextReference("reasoning-context", "1"), P.SourceReference("source", "1"),
    _authority, O.DemandReference("original-demand", "1"), Vector(1, 2).map(index => {
      val identity = P.WorkItemIdentity(s"item-$index")
      R.UnknownItem(identity, s"未知の推論-$index", B.Item(identity,
        Vector(B.ClosureCriterion(s"complete-$index")), Vector(P.WorkItemIdentity("item-0")), _authority))
    }), S.ResultReference("reasoning-result", "1"))
  private val _judgment = R.Judgment(R.Decision.Enriched, "Only the requested work was classified",
    _request.items.zip(Vector(R.Classification.ExpensiveReasoning, R.Classification.SettledExecution)).map { (item, classification) =>
      R.Assignment(item.identity, classification, s"根拠 ${item.identity.value}")
    }, Vector(S.EvidenceReference("source-analysis", "1")))

  // Common transport fixture only. It has no canonical native issue, runtime
  // grant, assigned participant or persisted continuation and cannot provide one.
  private def _frame(): WorkflowInteraction[R.Request, Nothing] = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("SplitPhaseWorkflow"),
      WorkflowDefinitionRevision("reasoning-wire-fixture"), InstanceIdentity("reasoning-wire-instance"))
    val context = ContextBundle("reasoning-transport", Vector(FrameworkReference("reasoning-context", "1")),
      Vector(FrameworkReference("source", "1")), ContextSnapshot("reasoning-wire-fixture", Some("source-1"), Some("workspace-1")))
    val request = ContinuationRequest(StateMachineRunIdentity("reasoning-wire-run"), ContinuationIdentity("reasoning-wire-continuation"),
      StateMachineRevision("2"), StateMachineRequiredOperationIdentity("reasoning-wire-capability"),
      StateMachineOperationIdentity("TestReasoningService", "classifyUnknown"),
      Some(TypedValue(J.requestCodec.typeIdentity, _request)), Some(StateMachineResultTypeReference(J.resultCodec.typeIdentity)),
      context, CompletionContract("reasoning-wire-completion", Vector(FrameworkReference("reasoning-context", "1"))),
      EvidenceContract("reasoning-wire-evidence", Vector(FrameworkReference("source-analysis", "1"))),
      StateMachineOperationIdentity("TestReasoningService", "completeClassification"))
    WorkflowInteraction(handle, WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector(CapabilityRequirement("reasoning-classification")), RiskLevel("bounded"), ReasoningLevel.Deep, true),
      MinimalPresentation("Classify unknown work", "Return classification data, not execution decisions")))
  }
  private def _result(order: WorkflowInteraction[R.Request, Nothing]): ContinuationResult[R.Judgment] = {
    val request = order.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request
    ContinuationResult(order.handle, request.runId, request.continuationId, request.expectedRevision, request.context.snapshot,
      TypedValue(J.resultCodec.typeIdentity, _judgment), FrameworkReference("reasoning-result", "1"),
      request.completion.requiredFacts, ExecutionEvidence(request.evidence.requiredEvidence))
  }
  private def _json(text: String): Json = parse(text).fold(error => throw new AssertionError(error), identity)
  private def _with_unused_fields(value: Json): Json = value.asObject match {
    case Some(fields) =>
      val annotated = Json.obj(fields.toVector.map { (key, child) =>
        // Map keys are consumed child identities, not passive record fields.
        key -> (if (key == "incomingByChild") child else _with_unused_fields(child))
      }*)
      // Circe sum constructors and empty case-object bodies are type syntax,
      // not business records to which passive annotation fields can be added.
      if (fields.isEmpty || (fields.size == 1 && fields.keys.head.headOption.exists(_.isUpper))) annotated
      else annotated.mapObject(_.add("unusedCommandGrant", Json.obj("execute" -> Json.True)))
    case None => value.asArray.map(items => Json.arr(items.map(_with_unused_fields)*)).getOrElse(value)
  }
  private def _rewrite(value: Json, path: List[String])(change: Json => Json): Json = path match {
    case Nil => change(value)
    case head :: tail => value.asObject match {
      case Some(objectvalue) => Json.fromJsonObject(objectvalue.add(head, _rewrite(objectvalue(head).get, tail)(change)))
      case None =>
        val array = value.asArray.get
        Json.fromValues(array.updated(head.toInt, _rewrite(array(head.toInt), tail)(change)))
    }
  }
  private def _object_mutations(value: Json): Vector[Json] = value.asObject match {
    case Some(objectvalue) =>
      objectvalue.keys.toVector.map(key => Json.fromJsonObject(objectvalue.remove(key))) ++
        objectvalue.toVector.flatMap { (key, child) =>
          _object_mutations(child).map(changed => Json.fromJsonObject(objectvalue.add(key, changed)))
        }
    case None => value.asArray.fold(Vector.empty[Json])(array => array.zipWithIndex.flatMap { (child, index) =>
      _object_mutations(child).map(changed => Json.fromValues(array.updated(index, changed)))
    })
  }

  "E2 Assigned reasoning replies have a closed request schema" must _assigned_reply_spec {
    "reject request.v1 and missing assignments rather than manufacturing a reply destination" in {
      Given("Spec: docs/spec/split-phase-reasoning-interface.md; Rules: R2; Example: E2; a complete v2 request, old v1 payload without a reply and crossed schema labels")
      val current = take(J.requestCodec.encodeC(_request))
      val missing = _rewrite(current, List("payload"))(_.mapObject(_.remove("result")))
      val old = _rewrite(missing, List("schemaVersion"))(_ => Json.fromString("sm-workflow.split-phase-reasoning-request.v1"))
      val mislabeled = _rewrite(current, List("schemaVersion"))(_ => Json.fromString("sm-workflow.split-phase-reasoning-request.v1"))

      When("the selected request codec receives those three unsupported inputs and the complete current request")
      val refused = Vector(old, mislabeled, missing).map(J.requestCodec.decode)
      val restored = J.requestCodec.decode(current)

      Then("only v2 with its explicit assignment survives, without an old-schema adapter or implicit result reference")
      refused.foreach(_.isLeft shouldBe true)
      restored shouldBe Right(_request)
      restored.toOption.get.result shouldBe S.ResultReference("reasoning-result", "1")
      current.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.split-phase-reasoning-request.v2")
      J.resultCodec.schema shouldBe "sm-workflow.split-phase-reasoning-result.v1"
    }
  }

  "Purpose-specific reasoning semantic transport" should {
    "application data" which {
      "round trip ordered unknown closure with original source, authority and demand without selecting Workflow actions" in {
        Given("two ordered unknown items carrying original closure, prerequisites and Japanese reasons")
        val request = _request

        When("the reasoning payload is encoded, pretty-printed and decoded independently of object member order")
        val wire = take(J.requestCodec.encodeC(request))
        val restored = J.requestCodec.decode(_json(wire.spaces2))
        val reordered = J.requestCodec.decode(wire.mapObject(objectvalue => io.circe.JsonObject.fromIterable(objectvalue.toVector.reverse)))

        Then("all original data and item ordering survive without executable state or a replacement reference")
        restored shouldBe Right(request)
        reordered shouldBe Right(request)
        restored.toOption.get.items.map(_.identity) shouldBe Vector(P.WorkItemIdentity("item-1"), P.WorkItemIdentity("item-2"))
        restored.toOption.get.items.head.closure.prerequisiteItems shouldBe Vector(P.WorkItemIdentity("item-0"))
        restored.toOption.get.authority shouldBe _authority
        restored.toOption.get.demand shouldBe O.DemandReference("original-demand", "1")
        restored.toOption.get.result shouldBe S.ResultReference("reasoning-result", "1")
      }

      "round trip both classifications and DecisionNeeded as raw data without granting semantic admission" in {
        Given("classified results, a decision-needed result and structurally complete but semantically incomplete responses")
        val decision = _judgment.copy(decision = R.Decision.DecisionNeeded, assignments = Vector.empty)
        val values = Vector(_judgment, decision, _judgment.copy(evidence = Vector.empty),
          _judgment.copy(assignments = _judgment.assignments.reverse),
          decision.copy(assignments = _judgment.assignments))

        When("the application codec restores each declared response")
        val restored = values.map(value => J.resultCodec.decode(take(J.resultCodec.encodeC(value))))

        Then("wire decoding retains values but creates neither an admitted classification nor a Human decision")
        restored shouldBe values.map(Right(_))
        restored.head.toOption.get.assignments.map(_.classification) shouldBe
          Vector(R.Classification.ExpensiveReasoning, R.Classification.SettledExecution)
        restored(1).toOption.get.assignments shouldBe empty
      }

      "reject missing consumed fields at every request and result object depth" in {
        Given("complete reasoning request and result payloads with nested typed references, closure and assignments")
        val request = take(J.requestCodec.encodeC(_request))
        val result = take(J.resultCodec.encodeC(_judgment))
        val requestchanges = _object_mutations(request)
        val resultchanges = _object_mutations(result)

        When("each object loses one consumed typed field")
        val requestresults = requestchanges.map(J.requestCodec.decode)
        val resultresults = resultchanges.map(J.resultCodec.decode)

        Then("missing typed business data fails without a duplicate nested-field verifier")
        requestchanges.nonEmpty shouldBe true
        resultchanges.nonEmpty shouldBe true
        requestresults.foreach(_.isLeft shouldBe true)
        resultresults.foreach(_.isLeft shouldBe true)
      }

      "decode reasoning Human business data without reparsing native work and result envelopes" in {
        Given("a typed request, judgment and planning frontier, with actual work/result references but no native authority")
        val input = SplitPhaseReasoningHumanDirection.Input(ContinuationIdentity("reasoning-slot"), _request,
          FrameworkReference("reasoning-result", "1"), _judgment,
          SplitPhasePlanningCheckpointFixture.capture().record.snapshot)
        val encoded = SplitPhaseReasoningHumanDirection.inputCodec.encode(input)
        val keys = Vector("workIdentity", "request", "resultReference", "judgment", "frontier")
        val missing = keys.map(key => encoded.hcursor.downField("payload").withFocus(_.mapObject(_.remove(key))).top.get)
        val malformed = missing ++ Vector(_rewrite(encoded, List("schemaVersion"))(_ => Json.fromString("foreign")),
          _rewrite(encoded, List("payload", "judgment"))(_ => Json.True))

        When("the existing business codecs consume the nested data and ignore passive extension annotations")
        val restored = SplitPhaseReasoningHumanDirection.inputCodec.decode(_with_unused_fields(encoded))
        val refused = malformed.map(SplitPhaseReasoningHumanDirection.inputCodec.decode)
        val target = NativeSplitAnswerScope.reasoningTargetC(input, _frame().handle)

        Then("business data survives once and the actual host target uses its work/reference rather than a native wire copy")
        restored shouldBe Right(input)
        refused.foreach(_.isLeft shouldBe true)
        encoded.noSpaces should not include "reasoningWork"
        encoded.noSpaces should not include "reasoningResult"
        target.isSuccess shouldBe true
        target.toOption.get.hcursor.downField("work").get[String]("continuationId").toOption shouldBe Some("reasoning-slot")
      }

      "decode declared passive Human interpretation data independently of presentation diagnostic and unused fields" in {
        Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R7; Example: E20; an enriched passive interpretation, not Human consent or execution authority")
        val record = SplitPhaseHumanReasoningResolution.Record(S.ContextReference("human-resolution", "1"),
          "human-slot", FrameworkReference("reasoning-result", "1"), _judgment)
        val wire = take(SplitPhaseHumanReasoningResolution.encodeC(record))
        val schema = SplitPhaseHumanReasoningResolution.schema
        val json = _json(wire.substring(schema.length + 1))
        val changed = _rewrite(json, List("interpretation", "payload", "rationale"))(_ => Json.fromString("別の説明だけを保持する"))
        val reordered = json.mapObject(fields => io.circe.JsonObject.fromIterable(fields.toVector.reverse))

        When("the record reader decodes declared data with reordered fields, whitespace or changed explanation")
        val original = SplitPhaseHumanReasoningResolution.decodeC(schema + ":" + reordered.spaces2)
        val diagnostic = SplitPhaseHumanReasoningResolution.decodeC(schema + ":" + changed.noSpaces)
        val annotated = SplitPhaseHumanReasoningResolution.decodeC(schema + ":" + _with_unused_fields(json).noSpaces)

        Then("the reader retains those actual values without rewriting a record or admitting a Workflow answer")
        take(original) shouldBe record
        take(annotated) shouldBe record
        take(diagnostic) shouldBe record.copy(interpretation = _judgment.copy(rationale = "別の説明だけを保持する"))
        J.resultCodec.decode(changed.hcursor.downField("interpretation").focus.get) shouldBe
          Right(_judgment.copy(rationale = "別の説明だけを保持する"))
      }

      "reject malformed Human interpretation fields and non-enriched record kind through declared decoding" in {
        Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R7; Example: E21; a complete interpretation and raw shape/type/kind/schema counterexamples")
        val schema = SplitPhaseHumanReasoningResolution.schema
        val record = SplitPhaseHumanReasoningResolution.Record(S.ContextReference("human-resolution", "1"),
          "human-slot", FrameworkReference("reasoning-result", "1"), _judgment)
        val wire = take(SplitPhaseHumanReasoningResolution.encodeC(record))
        val json = _json(wire.substring(schema.length + 1))
        val mutations = _object_mutations(json) ++ Vector(
          _rewrite(json, List("interpretation", "payload", "decision"))(_ => Json.fromString("DecisionNeeded")),
          _rewrite(json, List("interpretation", "payload", "assignments", "0", "classification"))(_ => Json.fromString("unrecognized")),
          _rewrite(json, List("reference", "revision"))(_ => Json.fromInt(1)),
          _rewrite(json, List("decision"))(_ => Json.Null),
          _rewrite(json, List("interpretation", "payload", "evidence"))(_ => Json.Null),
          _rewrite(json, List("schemaVersion"))(_ => Json.fromString("unsupported")))

        When("those passive records reach the selected reader without any encoder-based read certificate")
        val results = mutations.map(value => SplitPhaseHumanReasoningResolution.decodeC(schema + ":" + value.noSpaces))

        Then("every omission wrong type and unsupported kind refuses without semantic execution")
        mutations.size should be > 30
        results.foreach(_.isSuccess shouldBe false)
      }

      "E12 ignore unused context echoes and participant control claims" in {
        Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R5; Example: E12; a valid classification with obsolete context and extra claimed Workflow choices")
        val wire = take(J.resultCodec.encodeC(_judgment))
        val fields = Vector("context", "contextDigest", "planning", "demands", "costPolicy", "overheadPolicy", "nextOperation", "stage", "winner", "actor", "command")
        val altered = fields.map(field => _rewrite(wire, List("payload"))(value =>
          value.mapObject(_.add(field, Json.obj("identity" -> Json.fromString("participant-selected"))))))

        When("those proposed execution or policy controls reach application decoding")
        val results = altered.map(J.resultCodec.decode)

        Then("the participant supplies only classification data, not context selection or Workflow ownership")
        wire.hcursor.downField("payload").focus.get.asObject.get.keys.toSet shouldBe
          Set("decision", "rationale", "assignments", "evidence")
        results.foreach(_ shouldBe Right(_judgment))
      }

      "refuse crossed schemas, primitive values, null data and noncanonical enum spellings" in {
        Given("one valid reasoning result and malformed wire values")
        val wire = take(J.resultCodec.encodeC(_judgment))
        val invalid = Vector(Json.Null, Json.arr(), Json.fromString("reasoning"),
          _rewrite(wire, List("schemaVersion"))(_ => Json.fromString(J.requestCodec.schema)),
          _rewrite(wire, List("schemaVersion"))(_ => Json.fromString("sm-workflow.split-phase-scope-result.v1")),
          _rewrite(wire, List("schemaVersion"))(_ => Json.fromString("sm-workflow.split-phase-partition-semantic-result.v1")),
          _rewrite(wire, List("payload", "decision"))(_ => Json.fromString("enriched")),
          _rewrite(wire, List("payload", "assignments", "0", "classification"))(_ => Json.fromString("expensiveReasoning")),
          _rewrite(wire, List("payload", "evidence", "0", "revision"))(_ => Json.fromInt(1)),
          _rewrite(wire, List("payload", "evidence"))(_ => Json.Null))

        When("wire-level invalid data is decoded without alternate-purpose fallback")
        val results = invalid.map(J.resultCodec.decode) :+ J.resultCodec.decode(null)
        val crossed = J.resultCodec.decode(take(J.requestCodec.encodeC(_request)))

        Then("unsupported types, purposes and enum aliases are refused")
        results.foreach(_.isLeft shouldBe true)
        crossed.isLeft shouldBe true
      }

      "reject null nested values during encoding instead of emitting an incomplete payload" in {
        Given("requests and judgments whose Scala values contain absent nested structures")
        val item = _request.items.head
        val requests = Vector(null, _request.copy(context = null), _request.copy(source = null),
          _request.copy(authority = null), _request.copy(demand = null), _request.copy(result = null), _request.copy(items = null),
          _request.copy(items = Vector(null)), _request.copy(items = Vector(item.copy(closure = null))),
          _request.copy(items = Vector(item.copy(closure = item.closure.copy(closureCriteria = Vector(null))))))
        val judgments = Vector(null, _judgment.copy(decision = null),
          _judgment.copy(assignments = null), _judgment.copy(assignments = Vector(null)),
          _judgment.copy(assignments = Vector(_judgment.assignments.head.copy(classification = null))),
          _judgment.copy(evidence = null), _judgment.copy(evidence = Vector(null)))

        When("the checked encoders receive the malformed nested values")
        val requestresults = requests.map(J.requestCodec.encodeC)
        val resultresults = judgments.map(J.resultCodec.encodeC)

        Then("all malformed data fails structurally without throwing or manufacturing trusted state")
        requestresults.foreach(_ shouldBe a[Consequence.Failure[?]])
        resultresults.foreach(_ shouldBe a[Consequence.Failure[?]])
      }

      "carry reasoning evidence longer than4096 characters and bound the application payload by UTF-8 bytes" in {
        Given("a legitimate long rationale and a shorter-character-count rationale whose UTF-8 bytes exceed one MiB")
        val supported = _judgment.copy(rationale = "根拠" * 10000)
        val oversized = _judgment.copy(rationale = "あ" * (J.MAXIMUM_PAYLOAD_BYTES / 3 + 1))
        val oversizedwire = take(J.resultCodec.encodeC(_judgment)).hcursor.downField("payload").focus.get
          .mapObject(_.add("rationale", Json.fromString(oversized.rationale)))
        val envelope = Json.obj("schemaVersion" -> Json.fromString(J.resultCodec.schema), "payload" -> oversizedwire)

        When("the supported rationale is round-tripped and both oversized input routes are checked")
        val restored = J.resultCodec.decode(take(J.resultCodec.encodeC(supported)))
        val encoded = J.resultCodec.encodeC(oversized)
        val decoded = J.resultCodec.decode(envelope)

        Then("transport accepts ordinary long evidence but refuses the actual byte-limit excess")
        supported.rationale.length should be > 4096
        restored shouldBe Right(supported)
        oversized.rationale.length should be < J.MAXIMUM_PAYLOAD_BYTES
        envelope.noSpaces.getBytes(StandardCharsets.UTF_8).length should be > J.MAXIMUM_PAYLOAD_BYTES
        encoded shouldBe a[Consequence.Failure[?]]
        decoded.isLeft shouldBe true
      }
    }

    "CNCF common transport" which {
      "round trip WorkOrder and result frames and preserve their structural matching without pretending native issue" in {
        Given("a private transport-only WorkOrder and response carrying the reasoning application types")
        val order = _frame()
        val result = _result(order)

        When("both common CNCF envelopes encode, decode and perform their separate correlation match")
        val restoredorder = take(WorkflowWorkOrderJsonV1.decodeC(take(J.encodeWorkOrderC(order)), J.requestCodec))
        val restoredresult = take(WorkflowResultJsonV1.decodeC(take(J.encodeResultC(result)), J.resultCodec))
        val matched = take(WorkflowProtocolV1.admitResultC(restoredorder, restoredresult))

        Then("the payload and original correlation survive while no runtime claim or persisted continuation is created")
        restoredorder shouldBe order
        restoredresult shouldBe result
        matched.typeReference.value shouldBe "SplitPhaseReasoningSemanticResult"
        matched.contextReference shouldBe FrameworkReference("reasoning-result", "1")
      }

      "use the selected result decoder without adding an early type-copy refusal" in {
        Given("reasoning common frames and codecs for a different semantic purpose")
        val order = _frame()
        val requestwire = take(J.encodeWorkOrderC(order))
        val resultwire = take(J.encodeResultC(_result(order)))
        var requestcalls = 0
        var resultcalls = 0
        val foreignrequest = new WorkflowResultJsonV1.PayloadCodec[R.Request] {
          val typeIdentity = "SplitPhasePartitionSemanticRequest"
          def encode(value: R.Request): Json = Json.Null
          def decode(value: Json): Either[String, R.Request] = { requestcalls += 1; Left("wrong purpose") }
        }
        val foreignresult = new WorkflowResultJsonV1.PayloadCodec[R.Judgment] {
          val typeIdentity = "SplitPhaseScopeSemanticResult"
          def encode(value: R.Judgment): Json = Json.Null
          def decode(value: Json): Either[String, R.Judgment] = { resultcalls += 1; Left("wrong purpose") }
        }

        When("the WorkOrder input selector and the independently selected result decoder consume their respective data")
        val requestresult = WorkflowWorkOrderJsonV1.decodeC(requestwire, foreignrequest)
        val resultresult = WorkflowResultJsonV1.decodeC(resultwire, foreignresult)

        Then("the request selector refuses another purpose and the selected result decoder reports incompatible data without a fallback")
        requestresult shouldBe a[Consequence.Failure[?]]
        resultresult shouldBe a[Consequence.Failure[?]]
        requestcalls shouldBe 0
        resultcalls shouldBe 1
      }

      "accept transport diagnostics but refuse crossed common target, revision, model, completion and evidence correlations" in {
        Given("one structural WorkOrder and results that independently change its common binding")
        val order = _frame()
        val original = _result(order)
        val crossed = Vector(original.copy(handle = original.handle.copy(instanceIdentity = InstanceIdentity("other-instance"))),
          original.copy(runId = StateMachineRunIdentity("other-run")), original.copy(continuationId = ContinuationIdentity("other-task")),
          original.copy(expectedRevision = StateMachineRevision("3")),
          original.copy(contextSnapshot = original.contextSnapshot.copy(modelRevision = Some("other-model"))),
          original.copy(result = original.result.copy(typeIdentity = "SplitPhaseScopeSemanticResult")),
          original.copy(completionFacts = Vector.empty), original.copy(evidence = ExecutionEvidence(Vector.empty)))

        When("the independent common structural gate compares each result with the original order")
        val results = crossed.map(value => WorkflowProtocolV1.admitResultC(order, value))
        val diagnostic = WorkflowProtocolV1.admitResultC(order, original.copy(contextSnapshot = original.contextSnapshot.copy(
          workspaceRevision = Some("other-workspace"), evidenceRevision = Some("other-evidence"))))

        Then("no crossed common result can become matching reasoning data")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        diagnostic.isSuccess shouldBe true
      }
    }
  }
}
