package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/** Named value binding and generated result intake checks. Real operation execution is covered by
  * the normal three-workflow specs, not inferred from a codec round trip.
  * @since   Oct. 12, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowOperationValueBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail("Named value intake failed: " + other)
  }
  private def _request(properties: Property*): Request =
    Request.of(component = "org.simplemodeling.textus.SmWorkflow", service = "GoalPhaseService",
      operation = "startGoalPhase", arguments = Nil, switches = Nil, properties = properties.toList)

  "R1/R3 generated selected repair progress intake" should {
    "roundtrip the additive Request Decision and Advance fields through generated business readers" in {
      Given("saved typed assignment and question progress with their normal public declaration fields")
      val request = NativeHumanEvidenceFixture.work.copy(
        selectedPlan = NativeHumanEvidenceFixture.plan.copy(repairLimit = 2),
        repairProgress = Some(GoalPhaseRepairProgress.Snapshot(1, 1, 2)))
      val payload = GoalPhaseNativeWork.inputCodec.encode(request).hcursor.downField("payload").focus.get
      def _record_(value: Json): Record = _take(new RecordDecoder().json(value.noSpaces))
      val question = Json.obj("work" -> payload,
        "requestingResult" -> Json.obj("identity" -> Json.fromString("actual-result"), "revision" -> Json.fromString("1")),
        "result" -> Json.obj("outcome" -> Json.fromString("completed"), "summary" -> Json.fromString("Selected work"), "findings" -> Json.arr()),
        "reason" -> Json.fromString("Actual selected question"),
        "repairProgress" -> Json.obj("automaticCount" -> Json.fromInt(1), "ordinaryCount" -> Json.fromInt(0), "limit" -> Json.fromInt(2)))
      val wait = NativeWorkflowOperationOutput.goalWait("selected-instance", "Pending", "Actual retained condition")
      When("the generated readers consume present and absent optional context using declaration or wire names")
      val work = _take(V.GoalPhaseWorkRequest.createC(_record_(payload)))
      val human = _take(V.GoalPhaseOrderedDecisionRequest.createC(_record_(question)))
      val output = _take(V.GoalPhaseAdvanceResult.createC(_record_(Json.obj("session_id" -> Json.fromString("selected-instance"),
        "kind" -> Json.fromString("Request"), "message" -> Json.fromString("Selected assignment"),
        "repair_progress" -> Json.obj("automatic_count" -> Json.fromInt(1), "ordinary_count" -> Json.fromInt(1), "limit" -> Json.fromInt(2))))))
      val absent = _take(V.GoalPhaseAdvanceResult.createC(wait.toRecord()))
      val child = NativeWorkflowOperationOutput.goalChild("selected-instance",
        NativeWorkflowOperationOutput.repositoryWait("selected-instance", "Pending repository", "Actual child condition"))
      Then("Request and question retain distinct saved counts and context-free Wait stays unknown")
      work.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 2))
      human.work.repairProgress shouldBe work.repairProgress
      human.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 0, 2))
      output.repairProgress shouldBe work.repairProgress
      absent.repairProgress shouldBe None
      child.repairProgress shouldBe None
      _take(V.GoalPhaseWorkRequest.createC(work.toRecord())) shouldBe work
      _take(V.GoalPhaseOrderedDecisionRequest.createC(human.toRecord())) shouldBe human
      _take(V.GoalPhaseAdvanceResult.createC(output.toRecord())) shouldBe output
      Given("a missing required limit or a noninteger automatic count in a present public snapshot")
      val malformed = Vector(_record_(Json.obj("automatic_count" -> Json.fromInt(1), "ordinary_count" -> Json.fromInt(1))),
        _record_(Json.obj("automatic_count" -> Json.fromString("fractional"), "ordinary_count" -> Json.fromInt(1), "limit" -> Json.fromInt(2))))
      When("the generated required-field reader consumes those public values")
      val failures = malformed.map(V.GoalPhaseRepairProgress.createC)
      Then("missing or mistyped required fields fail typed public intake")
      failures.foreach(_.isSuccess shouldBe false)
    }
  }

  "Named Workflow business parameters" should {
    "preserve present empty JSON containers at the root and recursively" in {
      Given("a specification-owned object with present empty cells, ordered arrays and an exact large integer, while omitted is absent")
      val emptycells = Json.obj("emptyarray" -> Json.arr(), "emptyobject" -> Json.obj())
      val business = Json.obj(
        "emptyarray" -> Json.arr(), "emptyobject" -> Json.obj(),
        "nested" -> emptycells,
        "array" -> Json.arr(Json.obj(), Json.arr(), emptycells, Json.True, Json.fromInt(0), Json.fromString("ordered")),
        "boolean" -> Json.False, "text" -> Json.fromString("SPECIFICATION-OWNED value"),
        "integer" -> Json.fromBigDecimal(BigDecimal("9007199254740993")))
      val representations: Vector[Any] = Vector(business, business.noSpaces)

      When("recordC consumes Json and String named objects and the actual Records are serialized to business JSON")
      val actual = representations.map { value =>
        val record = _take(NativeWorkflowOperationInput.recordC(_request(Property("input", value, None)), "input"))
        parser.parse(_take(record.toJsonStringC)).fold(error => fail(error.message), identity)
      }

      Then("both representations retain every present value exactly, including precision and array order, without inventing omitted")
      actual shouldBe Vector.fill(representations.size)(business)
      actual.foreach(_.hcursor.downField("omitted").focus shouldBe None)
    }

    "refuse explicit JSON null at every object and array depth with a typed failure" in {
      Given("named business objects with root, nested object and differently nested array null cells")
      val businesses = Vector(
        Json.obj("field" -> Json.Null),
        Json.obj("nested" -> Json.obj("field" -> Json.Null)),
        Json.obj("array" -> Json.arr(Json.Null)),
        Json.obj("array" -> Json.arr(Json.arr(Json.True, Json.Null))),
        Json.obj("array" -> Json.arr(Json.obj("nested" -> Json.arr(Json.Null)))))
      val representations: Vector[Any] = businesses.flatMap(value => Vector(value, value.noSpaces))
      When("recordC consumes each actual Json and String named business object")
      val actual = representations.map(value =>
        NativeWorkflowOperationInput.recordC(_request(Property("input", value, None)), "input"))
      Then("every explicit null returns Consequence.Failure without throwing or normalizing it away")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "retain a complete Plan proposal with empty Step dependencies through the generated WorkResult reader" in {
      Given("a specification-owned revision Proposal whose complete raw Plan retains the selected empty dependencies")
      val plan = NativeHumanEvidenceFixture.work.selectedPlan.copy(revision = "2", effectiveAcceptance = None)
      val proposal = GoalPhasePlanRevision.Proposal("specification-public-input-proposal", "1",
        "SPECIFICATION-OWNED public-input revision", plan)
      val business = Json.obj("outcome" -> Json.fromString("decision-needed"),
        "summary" -> Json.fromString("SPECIFICATION-OWNED proposed revision"), "findings" -> Json.arr(),
        "planRevision" -> proposal.asJson)
      val representations: Vector[Any] = Vector(business, business.noSpaces)

      When("Json and String pass through valueC's generated WorkResult reader and the owning strict Proposal decoder")
      val actual = representations.map { value =>
        val result = _take(NativeWorkflowOperationInput.valueC[V.GoalPhaseWorkResult](
          _request(Property("result", value, None)), "result"))
        _take(GoalPhasePlanRevision.fromPublicProposalC(result.planRevision))
      }

      Then("both public representations produce the exact typed Proposal with the required empty dependencies present")
      proposal.plan.steps.map(_.dependencies) shouldBe Vector(Vector.empty[String])
      actual shouldBe Vector.fill(representations.size)(Some(proposal))
    }

    "reject absent and null Step dependencies through generated WorkResult and strict Proposal intake" in {
      Given("specification-owned complete Plan proposals altered only by removing dependencies or explicitly making them null")
      val plan = NativeHumanEvidenceFixture.work.selectedPlan.copy(revision = "2", effectiveAcceptance = None)
      val proposal = GoalPhasePlanRevision.Proposal("specification-invalid-dependencies", "1",
        "SPECIFICATION-OWNED invalid dependency cases", plan).asJson
      val planjson = proposal.hcursor.downField("plan").focus.get
      val steps = planjson.hcursor.downField("steps").focus.get.asArray.get
      val invalidsteps = Vector(steps.head.mapObject(_.remove("dependencies")),
        steps.head.mapObject(_.add("dependencies", Json.Null)))
      val businesses = invalidsteps.map { step =>
        val invalidplan = planjson.mapObject(_.add("steps", Json.fromValues(steps.updated(0, step))))
        Json.obj("outcome" -> Json.fromString("decision-needed"),
          "summary" -> Json.fromString("SPECIFICATION-OWNED invalid proposal"), "findings" -> Json.arr(),
          "planRevision" -> proposal.mapObject(_.add("plan", invalidplan)))
      }
      val representations: Vector[Any] = businesses.flatMap(value => Vector(value, value.noSpaces))

      When("each Json and String candidate traverses the generated WorkResult reader and owning strict Proposal decoder")
      val actual = representations.map { value =>
        NativeWorkflowOperationInput.valueC[V.GoalPhaseWorkResult](
          _request(Property("result", value, None)), "result").flatMap(result =>
          GoalPhasePlanRevision.fromPublicProposalC(result.planRevision))
      }

      Then("neither missing nor null dependencies become an accepted Proposal or receive an empty-array default")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject scalar and array JSON as named object inputs" in {
      Given("specification-owned scalar and array inputs in both Json and String representations")
      val values = Vector(Json.Null, Json.True, Json.fromInt(1), Json.fromString("scalar"), Json.arr(), Json.arr(Json.obj()))
      val representations: Vector[Any] = values.flatMap(value => Vector(value, value.noSpaces))

      When("recordC consumes each value as a named business object")
      val actual = representations.map(value =>
        NativeWorkflowOperationInput.recordC(_request(Property("input", value, None)), "input"))

      Then("every scalar and array retains the object-only boundary failure")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "consume a Goal Start DTO, its Record or business JSON without a universal command envelope" in {
      Given("the same requested phase and repository in the declared business representations")
      val input = V.GoalPhaseStartInput("phase-5.3", "sm-workflow")
      val requests = Vector(
        _request(Property("input", input, None)),
        _request(Property("input", input.toRecord(), None)),
        _request(Property("input", """{"phaseIdentity":"phase-5.3","repositoryIdentity":"sm-workflow"}""", None)))

      When("the named input is read through its generated business ValueReader")
      val actual = requests.map(request => _take(NativeWorkflowOperationInput.valueC[V.GoalPhaseStartInput](request, "input")))

      Then("each produces the requested typed value, without Start control or selection data")
      actual shouldBe Vector.fill(requests.size)(input)
    }

    "map the Split and Repository Start choices directly from their business Records" in {
      Given("Split preview and Repository branch/remote choices, not internal allocation commands")
      val split = V.SplitPhaseStartInput("phase-5.3", "PREVIEW", Some("proposal-1"))
      val repository = V.RepositorySyncStartInput("sm-workflow", "main", "origin", "main", "upstream", "FULL_STATE")

      When("the owning Start maps the declared Record to its native business input")
      val splitinput = _take(NativeWorkflowStartInput.fromRecordC(NativeWorkflowStartInput.Kind.SplitPhase, split.toRecord()))
      val repositoryinput = _take(NativeWorkflowStartInput.fromRecordC(NativeWorkflowStartInput.Kind.RepositorySync, repository.toRecord()))

      Then("only actual source/proposal and repository choices are carried into the action")
      splitinput shouldBe NativeWorkflowStartInput.Split("phase-5.3", "PREVIEW", Some("proposal-1"))
      repositoryinput shouldBe NativeWorkflowStartInput.Repository("sm-workflow", "main", "origin", "main", "upstream", "FULL_STATE")
    }

    "consume outcome, summary and multiple findings as the named work result" in {
      Given("a semantic work result supplied without value/payload/schema wrappers")
      val result = V.GoalPhaseWorkResult("accepted", "Implemented the selected Step.", Vector("one", "two"), None, None, None, None, None, None, None, None, dependencyValidation = None)
      val request = _request(Property("sessionId", "execution-1", None), Property("result", result, None))

      When("the result and canonical session parameters are read")
      val actual = _take(NativeWorkflowOperationInput.valueC[V.GoalPhaseWorkResult](request, "result"))
      val session = _take(NativeWorkflowOperationInput.textC(request, "sessionId"))

      Then("all business findings survive typed intake and the explicit session is retained")
      actual shouldBe result
      session shouldBe "execution-1"
    }

    "consume nested Human resolution values without re-encoding a continuation" in {
      Given("a Human answer whose declared choice carries an actual resolution reference")
      val answer = V.SplitPhaseHumanDirectionAnswer(V.SplitPhaseHumanDirectionChoice("ProvideResolution"),
        Some(V.SplitPhasePartitionContextReference("human-resolution-1", "1")))
      val request = _request(Property("answer", answer, None))

      When("the generated named answer reader consumes its nested business data")
      val actual = _take(NativeWorkflowOperationInput.valueC[V.SplitPhaseHumanDirectionAnswer](request, "answer"))

      Then("choice and resolution remain typed values rather than a returned native control envelope")
      actual shouldBe answer
    }

    "read only genuine supplied artifact references and leave omitted evidence absent" in {
      Given("two actual completion fact references supplied as Records or a JSON array")
      val first = V.WorkflowArtifactReference("checks-1", "1")
      val second = V.WorkflowArtifactReference("review-1", "2")
      val requests = Vector(
        _request(Property("completionFacts", Vector(first.toRecord(), second.toRecord()), None)),
        _request(Property("completionFacts", """[{"identity":"checks-1","revision":"1"},{"identity":"review-1","revision":"2"}]""", None)))

      When("the named collection and optional execution evidence are consumed")
      val facts = requests.map(request => _take(NativeWorkflowOperationInput.vectorC[V.WorkflowArtifactReference](request, "completionFacts")))
      val absent = _take(NativeWorkflowOperationInput.optionalValueC[V.WorkflowExecutionEvidence](requests.head, "executionEvidence"))

      Then("the actual references remain ordered and no evidence certificate is synthesized")
      facts shouldBe Vector.fill(requests.size)(Vector(first, second))
      absent shouldBe None
    }

    "ignore unused diagnostics without using them to select or prove unchanged business data" in {
      Given("a real named input alongside unused legacy-looking diagnostic parameters")
      val input = V.GoalPhaseStartInput("phase-5.3", "sm-workflow")
      val request = _request(Property("input", input, None), Property("selectionIdentity", false, None),
        Property("contextIdentity", Json.arr(), None), Property("arg1", "not an input", None))

      When("the named business boundary consumes input alone")
      val actual = _take(NativeWorkflowOperationInput.valueC[V.GoalPhaseStartInput](request, "input"))

      Then("unused annotations neither reject the request nor replace the real business choice")
      actual shouldBe input
    }

    "report an absent consumed parameter instead of treating positional arg1 as a fallback" in {
      Given("only the retired universal argument, with no declared named business input")
      val request = _request().copy(arguments = List(Argument("arg1", """{"input":{"phaseIdentity":"phase-5.3","repositoryIdentity":"sm-workflow"}}""")))

      When("Start consumes its required named input")
      val actual = NativeWorkflowOperationInput.valueC[V.GoalPhaseStartInput](request, "input")

      Then("the actual missing business input fails without a legacy transport adapter")
      actual shouldBe a[Consequence.Failure[?]]
    }
  }

  "B1 generated workflow wire result intake" must afterWord(
    "in spec:scala3-record-input-keys, example:B1, rules:R1,R5, phase:sm-workflow-6"
  ) {
    "consume strict JSON wire Records through all three compiled StartResult readers" in {
      Given("Spec: simple-modeler/docs/spec/scala3-record-input-keys.md; Rules: R1,R5; Example: B1; strict JSON with session_id, outcome and a Japanese message")
      val record = _take(new RecordDecoder().json(
        """{"session_id":"actual-wire-1","outcome":"started","message":"日本語 wire"}"""
      ))

      When("the compiled generated Goal, Split and Repository StartResult readers consume the wire Record")
      val goal = _take(V.GoalPhaseStartResult.createC(record))
      val split = _take(V.SplitPhaseStartResult.createC(record))
      val repository = _take(V.RepositorySyncStartResult.createC(record))

      Then("the supplied wire session, outcome and message survive as exact typed results")
      goal shouldBe V.GoalPhaseStartResult("actual-wire-1", "started", "日本語 wire")
      split shouldBe V.SplitPhaseStartResult("actual-wire-1", "started", "日本語 wire")
      repository shouldBe V.RepositorySyncStartResult("actual-wire-1", "started", "日本語 wire")
    }
  }

  "B2 generated result priority and required fields" must afterWord(
    "in spec:scala3-record-input-keys, example:B2, rules:R2,R3, phase:sm-workflow-6"
  ) {
    "prefer the declared session name and retain failures for absent required values" in {
      Given("Spec: simple-modeler/docs/spec/scala3-record-input-keys.md; Rules: R2,R3; Example: B2; simultaneous declared and wire sessions plus missing-session and missing-message Records")
      val record = Record.data(
        "sessionId" -> "declared-1", "session_id" -> "wire-2",
        "outcome" -> "started", "message" -> "kept"
      )
      val incomplete = Vector(
        Record.data("outcome" -> "started", "message" -> "kept"),
        Record.data("session_id" -> "wire-2", "outcome" -> "started")
      )

      When("all three compiled generated readers consume the supplied candidates and incomplete Records")
      val goal = _take(V.GoalPhaseStartResult.createC(record))
      val split = _take(V.SplitPhaseStartResult.createC(record))
      val repository = _take(V.RepositorySyncStartResult.createC(record))
      val failures = incomplete.flatMap { input =>
        Vector(
          V.GoalPhaseStartResult.createC(input),
          V.SplitPhaseStartResult.createC(input),
          V.RepositorySyncStartResult.createC(input)
        )
      }

      Then("the declaration wins over the wire fallback and every missing required field remains a Consequence failure")
      goal shouldBe V.GoalPhaseStartResult("declared-1", "started", "kept")
      split shouldBe V.SplitPhaseStartResult("declared-1", "started", "kept")
      repository shouldBe V.RepositorySyncStartResult("declared-1", "started", "kept")
      failures.foreach { result =>
        result shouldBe a[Consequence.Failure[?]]
      }
    }
  }

  "B3 generated issued-work context intake" must afterWord(
    "in spec:native-issued-work-context, example:B3, rules:R1,R2,R5, phase:6"
  ) {
    "consume the declared nested view through all three strict wire readers and reject missing required context values" in {
      Given("a specification-owned issued view with native Boolean requirement, references, snapshot and operation routes")
      val handle = Record.data("componentIdentity" -> "org.simplemodeling.textus.SmWorkflow",
        "workflowIdentity" -> "FixtureWorkflow", "workflowRevision" -> "fixture-v1",
        "instanceIdentity" -> "wire-instance", "protocolVersion" -> "cncf.workflow-protocol.v1")
      val reference = Record.data("identity" -> "wire-fact", "revision" -> "3")
      val context = Record.data("summary" -> "Assigned wire context", "requiredFacts" -> Vector(reference),
        "references" -> Vector(reference), "snapshot" -> Record.data("workflowRevision" -> "fixture-v1"))
      val operation = Record.data("service" -> "FixtureService", "operation" -> "perform")
      val completion = Record.data("identity" -> "wire-completion", "requiredFacts" -> Vector(reference))
      val evidence = Record.data("identity" -> "wire-evidence", "requiredEvidence" -> Vector(reference))
      val completionoperation = Record.data("service" -> "FixtureService", "operation" -> "submit")
      val requirement = Record.data("capabilities" -> Vector("fixture-capability"), "risk" -> "bounded",
        "reasoning" -> "DEEP", "reviewRequired" -> true,
        "disposition" -> Record.data("placement" -> "DELEGATED", "independence" -> "REQUIRED",
          "originContextIdentity" -> "fixture-origin"), "reasoningClass" -> "FixtureSemanticClass")
      val view = V.WorkflowIssuedWorkContext("review", "cncf.workflow-protocol.v1", handle, "wire-run",
        "wire-continuation", "7", "fixture-capability", operation, Some("FixtureInput"), Some("FixtureResult"),
        context, completion, evidence, completionoperation, requirement)
      def _wire_(value: Record): Record = _take(new RecordDecoder().json(_take(Record.data(
        "session_id" -> "wire-instance", "kind" -> "Request", "message" -> "Assigned",
        "issued_work_context" -> value).toJsonStringC)))
      val nested = _take(new RecordDecoder().json(_take(view.toRecord().toJsonStringC)))
      val missing = Vector("context", "handle", "runId").flatMap { field =>
        Vector(nested.filterFields(_.key != field), nested.filterFields(_.key != field) ++ Record.createFull(Vector(field -> null)))
      }

      When("the three generated AdvanceResult readers consume the strict nested wire and incomplete candidates")
      val supplied = _wire_(nested)
      val actual = Vector(_take(V.GoalPhaseAdvanceResult.createC(supplied)).issuedWorkContext,
        _take(V.RepositorySyncAdvanceResult.createC(supplied)).issuedWorkContext,
        _take(V.SplitPhaseAdvanceResult.createC(supplied)).issuedWorkContext)
      val failures = missing.flatMap(value => Vector(V.GoalPhaseAdvanceResult.createC(_wire_(value)),
        V.RepositorySyncAdvanceResult.createC(_wire_(value)), V.SplitPhaseAdvanceResult.createC(_wire_(value))))

      Then("the generated view retains each selected contract and required missing/null fields fail at intake")
      actual.foreach { value =>
        value shouldBe Some(view)
        val consumed = value.get
        consumed.thinkingMode shouldBe "review"
        consumed.handle.getString("instanceIdentity") shouldBe Some("wire-instance")
        consumed.runId shouldBe "wire-run"
        consumed.continuationId shouldBe "wire-continuation"
        consumed.expectedRevision shouldBe "7"
        consumed.operation.getString("operation") shouldBe Some("perform")
        consumed.completionOperation.getString("operation") shouldBe Some("submit")
        consumed.executionRequirement.getBoolean("reviewRequired") shouldBe Some(true)
        consumed.executionRequirement.getString("reasoningClass") shouldBe Some("FixtureSemanticClass")
        consumed.executionRequirement.getRecord("disposition").get.getString("independence") shouldBe Some("REQUIRED")
        consumed.context.getRecord("snapshot").get.getString("workflowRevision") shouldBe Some("fixture-v1")
        consumed.completion.getVector("requiredFacts").get.head.asInstanceOf[Record].getString("identity") shouldBe Some("wire-fact")
        consumed.evidence.getVector("requiredEvidence").get.head.asInstanceOf[Record].getString("revision") shouldBe Some("3")
      }
      failures.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
