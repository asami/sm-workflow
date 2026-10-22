package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Recording ports establish consumer behavior only, never actual provider acceptance.
  * @since   Oct. 17, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseScopedQualityReviewSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhaseScopedQualityReview.*
  import ConfigurationValue.*
  private val _checks = Vector("semantic-consistency", "implementation-quality", "excessive-checks", "integration")
  private val _context = GoalPhaseNativeReview.Context(Vector("one.scala"), "actual candidate diff", Vector.empty)
  private val _input = NativeHumanEvidenceFixture.work.copy(kind = "ReviewStep", generation = 2,
    review = Some(_context))
  private val _handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("specification"), InstanceIdentity("instance"))
  private def _submitted(outcome: String = "accept") = ContinuationResult(_handle,
    StateMachineRunIdentity("fixture-run"), ContinuationIdentity("fixture-review"), StateMachineRevision("3"),
    ContextSnapshot("specification"), TypedValue(GoalPhaseNativeWork.resultCodec.typeIdentity,
      GoalPhaseNativeWork.Result(outcome, "Fixture disposition", Vector.empty)),
    ContextReference("independent-review-result", "7"), Vector.empty, ExecutionEvidence(Vector.empty, Some("fixture-worker")))
  private def _parameters(entries: (String, ConfigurationValue)*): ResolvedParameters =
    ResolvedParameters.fromResolvedConfiguration(ResolvedConfiguration(Configuration(entries.toMap), ConfigurationTrace.empty))
  private def _model(filename: String = "model/metadata.json", subjects: Vector[String] = Vector("component:SmWorkflow")) =
    _parameters(metadataKey -> StringValue(filename), subjectsKey -> ListValue(subjects.map(StringValue.apply).toList))
  private def _field[A](record: Record, name: String): A =
    record.fields.find(_.key == name).get.value.single.asInstanceOf[A]
  private def _star[A](record: Record, name: String): Vector[A] =
    record.fields.find(_.key == name).map(_.value.single.asInstanceOf[Vector[A]]).getOrElse(Vector.empty)
  private def _replace(record: Record, entries: (String, Any)*): Record = {
    val names = entries.map(_._1).toSet
    Record.dataAuto((record.fields.filterNot(f => names(f.key)).map(f => f.key -> f.value.single) ++ entries)*)
  }
  private def _omit(record: Record, names: String*): Record =
    Record.dataAuto(record.fields.filterNot(f => names.contains(f.key)).map(f => f.key -> f.value.single)*)

  // An independently constructed ordinary response using observed request references/scope.
  private def _reply(request: Record): Record = Record.dataAuto(
    "reviewId" -> _field[String](request, "reviewId"), "revision" -> _field[Long](request, "revision"),
    "subjectId" -> _field[String](request, "subjectId"), "subjectRevision" -> _field[String](request, "subjectRevision"),
    "status" -> "completed", "providerId" -> "fixture-provider", "purpose" -> "car-review.semantic-consistency",
    "ruleRevision" -> "cbd-scoped-quality-v1", "checks" -> _field[Vector[String]](request, "checks").map { check =>
      Record.dataAuto("checkId" -> check, "status" -> "covered",
        "paths" -> (if (check == "cml-contract") Vector.empty[String] else _field[Vector[String]](request, "paths")),
        "modelSubjects" -> _star[String](request, "modelSubjects"))
    })
  private class RecordingPort(
    files: Map[String, Option[String]] = Map("one.scala" -> Some("actual source")),
    respond: Record => Consequence[Record] = request => Consequence.success(_reply(request)),
    failRead: Boolean = false
  ) extends Port {
    var reads = Vector.empty[String]
    var requests = Vector.empty[Record]
    def readTextC(filename: String): Consequence[Option[String]] = {
      reads :+= filename
      if (failRead) Consequence.resourceInvalid("fixture file acquisition failed")
      else Consequence.success(files.getOrElse(filename, None))
    }
    def reviewC(request: Record): Consequence[Record] = {
      requests :+= request
      respond(request)
    }
  }
  private def _run(port: Port, input: GoalPhaseNativeWork.Request = _input,
    parameters: ResolvedParameters = _parameters(), outcome: String = "accept") =
    admitC(input, _submitted(outcome), parameters, port)

  "Normal scoped quality admission (spec:goal-phase-scoped-quality-admission R5-R8)" should {
    "B2-01 admit a clean ordinary response with exact assigned scope, materials and requirements" in {
      Given("an assigned candidate review with current source, diff, document acceptance and saved effective conditions")
      val boundary = GoalPhaseAcceptanceBoundary
      val effective = boundary.Effective(Some("Selected usage"), Vector(
        boundary.EffectiveCondition("step-quality", "step", true, "Actual step condition", "project"),
        boundary.EffectiveCondition("phase-quality", "phase", true, "Phase-only condition", "project")))
      val documents = GoalPhaseDocumentSource.Bound(GoalPhaseDocumentSource.Source("docs/phase.md", "docs/checklist.md"),
        Vector(GoalPhaseDocumentPlan.Binding("step", Vector("item"))),
        Vector(GoalPhaseDocumentPlan.Item("item", "Actual document condition", false)))
      val input = _input.copy(selectedPlan = _input.selectedPlan.copy(effectiveAcceptance = Some(effective)), documents = Some(documents))
      val port = new RecordingPort()
      When("the accept disposition acquires the assigned material and evaluates the returned Record through CAM")
      val result = _run(port, input)
      Then("one ordinary request retains exact references, semantic checks and actual material, retains explicit empty modelSubjects, and admission succeeds")
      result.isSuccess shouldBe true
      port.reads shouldBe Vector("one.scala")
      port.requests.size shouldBe 1
      val request = port.requests.head
      request.fields.map(_.key).toSet shouldBe Set("reviewId", "revision", "subjectId", "subjectRevision", "checks", "paths", "modelSubjects", "requirements", "materials")
      _field[String](request, "reviewId") shouldBe "independent-review-result"
      _field[Long](request, "revision") shouldBe 3L
      _field[String](request, "subjectId") shouldBe input.instanceIdentity
      _field[String](request, "subjectRevision") shouldBe input.sourceRevision
      _field[Vector[String]](request, "checks") shouldBe _checks
      _field[Vector[String]](request, "paths") shouldBe Vector("one.scala")
      request.fields.map(_.key) should contain ("modelSubjects")
      _field[Vector[String]](request, "modelSubjects") shouldBe Vector.empty[String]
      val materials = _field[Vector[Record]](request, "materials")
      materials.map(_field[String](_, "identity")) shouldBe Vector("source-1", "diff-1")
      materials.map(_field[String](_, "kind")) shouldBe Vector("source", "diff")
      materials.map(_field[String](_, "content")) shouldBe Vector("actual source", _context.diff)
      _field[String](materials.head, "path") shouldBe "one.scala"
      val requirements = _field[Vector[String]](request, "requirements")
      requirements.head shouldBe input.purpose
      requirements.exists(_.contains("Actual document condition")) shouldBe true
      requirements should contain allOf ("Selected usage", "Actual step condition")
      requirements should not contain "Phase-only condition"
      requirements.exists(_.contains("Fixture disposition")) shouldBe false
    }

    val defects: Vector[(String, Record => Record)] = Vector(
      "missing result" -> (_ => null),
      "missing checks" -> (r => _omit(r, "checks")),
      "wrong subject" -> (r => _replace(r, "subjectId" -> "another-instance")),
      "wrong subject revision" -> (r => _replace(r, "subjectRevision" -> "another-revision")),
      "stale review" -> (r => _replace(r, "reviewId" -> "another-review")),
      "stale generation" -> (r => _replace(r, "revision" -> 2L)),
      "stale rule" -> (r => _replace(r, "ruleRevision" -> "old-rule")),
      "partial scope" -> (r => _replace(r, "checks" -> _field[Vector[Record]](r, "checks").map(c => _replace(c, "paths" -> Vector.empty[String])))),
      "failed response" -> (r => _replace(r, "status" -> "failed")),
      "unavailable response" -> (r => _replace(r, "status" -> "unavailable")),
      "failed check" -> (r => _replace(r, "checks" -> _field[Vector[Record]](r, "checks").map(c => _replace(c, "status" -> "failed")))),
      "limitations" -> (r => _replace(r, "checks" -> _field[Vector[Record]](r, "checks").map(c => _replace(c, "limitations" -> Vector("coverage gap"))))),
      "missing provider" -> (r => _omit(r, "providerId")),
      "missing semantic purpose" -> (r => _omit(r, "purpose")),
      "malformed optional arrays" -> (r => _replace(r, "findings" -> "[]")),
      "blocker" -> (r => _replace(r, "findings" -> Vector(Record.dataAuto("identity" -> "issue-1",
        "checkId" -> "implementation-quality", "severity" -> "blocker", "path" -> "one.scala", "message" -> "Actual blocker")))))
    defects.foreach { (name, change) =>
      s"B2-02 reject $name returned by the recording provider" in {
        Given(s"a normal assigned review whose independently constructed response contains $name")
        val port = new RecordingPort(respond = request => Consequence.success(change(_reply(request))))
        When("the actual returned ordinary Record reaches decoding and CAM admission")
        val result = _run(port)
        Then("accept fails after exactly one review call without a successful-coverage default")
        result.isSuccess shouldBe false
        port.requests.size shouldBe 1
        port.reads shouldBe Vector("one.scala")
      }
    }

    "B2-03 retain an admitted gate with hygiene and development-candidate advisories" in {
      Given("full coverage and two independently returned nonblocking findings")
      val port = new RecordingPort(respond = request => Consequence.success(_replace(_reply(request),
        "findings" -> Vector("hygiene", "development-candidate").zipWithIndex.map { (severity, index) =>
          Record.dataAuto("identity" -> s"advisory-$index", "checkId" -> "integration", "severity" -> severity,
            "path" -> "one.scala", "message" -> "Follow-up suggestion")
        })))
      When("the response is evaluated through the existing findings gate")
      val result = _run(port)
      Then("the advisories allow acceptance with one review call")
      result.isSuccess shouldBe true
      port.requests.size shouldBe 1
    }

    "B2-04 retain explicit empty modelSubjects and accept omitted optional response arrays with mandatory semantic checks" in {
      Given("a no-CML review whose ordinary request retains explicit empty modelSubjects and whose response omits findings, modelSubjects and limitations")
      val port = new RecordingPort(respond = request => Consequence.success(_replace(_reply(request),
        "checks" -> _field[Vector[Record]](_reply(request), "checks").map(c => _omit(c, "modelSubjects", "limitations")))))
      When("the normal-shaped response is decoded and gated")
      val result = _run(port)
      Then("the request retains explicit empty modelSubjects, all four semantic checks remain required, and cml-contract is explicitly outside this selection")
      result.isSuccess shouldBe true
      port.requests.head.fields.map(_.key) should contain ("modelSubjects")
      _field[Vector[String]](port.requests.head, "modelSubjects") shouldBe Vector.empty[String]
      _field[Vector[String]](port.requests.head, "checks") shouldBe _checks
      _field[Vector[String]](port.requests.head, "checks") should not contain "cml-contract"
    }

    Vector("repair", "decision-needed").foreach { outcome =>
      s"B2-05 skip acquisition for $outcome after the independent host boundary" in {
        Given(s"a $outcome disposition and deliberately unusable review/configuration data")
        val port = new RecordingPort()
        When("the accept-only consumer is invoked")
        val result = admitC(null, _submitted(outcome), null, port)
        val production = admitC(null, null, _submitted(outcome))
        Then("no file or provider acquisition occurs and existing lifecycle handling remains available")
        result.isSuccess shouldBe true
        production.isSuccess shouldBe true
        port.reads shouldBe empty
        port.requests shouldBe empty
      }
    }

    val invalidInputs = Vector(
      "missing review" -> _input.copy(review = None),
      "empty paths" -> _input.copy(review = Some(_context.copy(changedPaths = Vector.empty))),
      "duplicate paths" -> _input.copy(review = Some(_context.copy(changedPaths = Vector("one.scala", "one.scala")))),
      "too many paths" -> _input.copy(review = Some(_context.copy(changedPaths = (1 to 65).map(i => s"$i.scala").toVector))),
      "negative generation" -> _input.copy(generation = -1),
      "overflow generation" -> _input.copy(generation = Long.MaxValue),
      "unbounded subject" -> _input.copy(instanceIdentity = "x" * 129),
      "controlled revision" -> _input.copy(sourceRevision = "3\n")) ++
      Vector("../one.scala", "src/../one.scala", "/one.scala", "file:one.scala", "https://example.invalid/one.scala",
        "one\n.scala", "C:/one.scala", "src\\one.scala", "./one.scala").zipWithIndex.map { (path, index) =>
        s"unsafe path $index" -> _input.copy(review = Some(_context.copy(changedPaths = Vector(path))))
      }
    invalidInputs.foreach { (name, input) =>
      s"B2-06 reject $name before material or provider acquisition" in {
        Given(s"assigned input with $name")
        val port = new RecordingPort()
        When("accept attempts to select its actual review scope")
        val result = _run(port, input)
        Then("the invalid assignment fails without reads or review calls")
        result.isSuccess shouldBe false
        port.reads shouldBe empty
        port.requests shouldBe empty
      }
    }

    val _oneSubject = ListValue(List(StringValue("term")))
    val invalidConfigurations = Vector(
      "metadata only" -> _parameters(metadataKey -> StringValue("metadata.json")),
      "subjects only" -> _parameters(subjectsKey -> _oneSubject),
      "null metadata" -> _parameters(metadataKey -> NullValue, subjectsKey -> _oneSubject),
      "null metadata string" -> _parameters(metadataKey -> StringValue(null), subjectsKey -> _oneSubject),
      "null subject list" -> _parameters(metadataKey -> StringValue("metadata.json"), subjectsKey -> ListValue(null)),
      "null subject string" -> _model(subjects = Vector(null)),
      "nonstrings" -> _parameters(metadataKey -> StringValue("metadata.json"), subjectsKey -> ListValue(List(NumberValue(BigDecimal(1))))),
      "scalar subjects" -> _parameters(metadataKey -> StringValue("metadata.json"), subjectsKey -> StringValue("term")),
      "empty subjects" -> _model(subjects = Vector.empty),
      "duplicate subjects" -> _model(subjects = Vector("term", "term")),
      "oversize subjects" -> _model(subjects = Vector("x" * 1025)),
      "nonexact subjects" -> _model(subjects = Vector(" term")),
      "too many subjects" -> _model(subjects = (1 to 65).map(i => s"term:$i").toVector),
      "URI metadata" -> _model(filename = "file:metadata.json"),
      "parent metadata" -> _model(filename = "../metadata.json"),
      "wrong metadata type" -> _parameters(metadataKey -> NumberValue(BigDecimal(1)), subjectsKey -> _oneSubject))
    invalidConfigurations.foreach { (name, parameters) =>
      s"B2-07 fail closed for $name configuration before acquisition" in {
        Given(s"current ResolvedParameters containing $name")
        val port = new RecordingPort()
        When("the accept-only consumer resolves the two configured properties")
        val result = _run(port, parameters = parameters)
        Then("the invalid configuration prevents all file and provider effects")
        result.isSuccess shouldBe false
        port.reads shouldBe empty
        port.requests shouldBe empty
      }
    }

    "B2-08 require model configuration for an actual changed CML path" in {
      Given("an actual changed model.cml path without either model property")
      val input = _input.copy(review = Some(_context.copy(changedPaths = Vector("model.cml"))))
      val port = new RecordingPort()
      When("accept selects its review scope")
      val result = _run(port, input)
      Then("missing CML metadata fails before reads or provider invocation")
      result.isSuccess shouldBe false
      port.reads shouldBe empty
      port.requests shouldBe empty
    }

    "B2-09 acquire exact configured metadata and full termId scope through the logical file port" in {
      Given("current model metadata and two exact actual Cozy termIds with an assigned changed CML file")
      val metadata = "actual Cozy model metadata bytes"
      val subjects = Vector("component:SmWorkflow", "operation:GoalPhaseService.submitGoalPhaseWorkResult")
      val port = new RecordingPort(Map("model.cml" -> Some("actual CML source"), "model/metadata.json" -> Some(metadata)))
      val input = _input.copy(review = Some(_context.copy(changedPaths = Vector("model.cml"))))
      When("the configured accept review reads material and gates ordinary full-scope coverage")
      val result = _run(port, input, _model(subjects = subjects))
      Then("the exact metadata appears once without an invented modelSubject, and all selected checks retain full scope")
      result.isSuccess shouldBe true
      port.reads shouldBe Vector("model.cml", "model/metadata.json")
      port.requests.size shouldBe 1
      val request = port.requests.head
      _field[Vector[String]](request, "checks") shouldBe Vector("cml-contract") ++ _checks
      _field[Vector[String]](request, "modelSubjects") shouldBe subjects
      val materials = _field[Vector[Record]](request, "materials").filter(_field[String](_, "kind") == "cml-metadata")
      materials.size shouldBe 1
      _field[String](materials.head, "content") shouldBe metadata
      materials.head.fields.map(_.key) should not contain "modelSubject"
    }

    "B2-10 reject missing configured metadata before provider invocation" in {
      Given("valid explicit model scope whose selected metadata file has no text")
      val port = new RecordingPort()
      When("accept acquires the exact configured file")
      val result = _run(port, parameters = _model())
      Then("metadata absence fails instead of silently omitting cml-contract")
      result.isSuccess shouldBe false
      port.reads shouldBe Vector("one.scala", "model/metadata.json")
      port.requests shouldBe empty
    }

    "B2-11 resolve changed current ExecutionContext configuration freshly between calls" in {
      Given("one execution context whose current configuration changes from no model scope to explicit scope")
      val context = ExecutionContext.create()
      val port = new RecordingPort(Map("one.scala" -> Some("source"), "model/metadata.json" -> Some("metadata")))
      RuntimeBindingAdmissionFixture.withResolvedParameters(context, _parameters())
      When("the package seam consumes the public current parameter projection on each invocation")
      val first = admitC(_input, _submitted(), context.resolvedParameters, port)
      RuntimeBindingAdmissionFixture.withResolvedParameters(context, _model())
      val second = admitC(_input, _submitted(), context.resolvedParameters, port)
      Then("the second request selects the new model scope and acquires only the newly selected metadata")
      first.isSuccess shouldBe true
      second.isSuccess shouldBe true
      port.requests.map(_field[Vector[String]](_, "checks")) shouldBe Vector(_checks, Vector("cml-contract") ++ _checks)
      port.reads shouldBe Vector("one.scala", "one.scala", "model/metadata.json")
    }

    "B2-12 keep deleted paths and actual diff without inventing source text" in {
      Given("an assigned deleted file with missing text and an actual deletion diff")
      val port = new RecordingPort(Map("one.scala" -> None))
      When("accept acquires the selected path")
      val result = _run(port)
      Then("the request retains its path scope and diff while omitting only the unavailable source material")
      result.isSuccess shouldBe true
      _field[Vector[String]](port.requests.head, "paths") shouldBe Vector("one.scala")
      val materials = _field[Vector[Record]](port.requests.head, "materials")
      materials.map(_field[String](_, "kind")) shouldBe Vector("diff")
      _field[String](materials.head, "content") shouldBe _context.diff
    }

    "B2-13 propagate File and provider failures without fallback" in {
      Given("a File failure and a separate unavailable provider result")
      val filePort = new RecordingPort(failRead = true)
      val providerPort = new RecordingPort(respond = _ => Consequence.serviceUnavailable("fixture provider absent"))
      When("accept attempts each required acquisition")
      val fileResult = _run(filePort)
      val providerResult = _run(providerPort)
      Then("both failures prevent acceptance and File failure reaches no provider")
      fileResult.isSuccess shouldBe false
      providerResult.isSuccess shouldBe false
      filePort.requests shouldBe empty
      providerPort.requests.size shouldBe 1
    }

    val boundedCases = Vector(
      "UTF8 source material" -> (_input, Map("one.scala" -> Some("界" * 87382))),
      "diff material" -> (_input.copy(review = Some(_context.copy(diff = "d" * 262145))), Map("one.scala" -> Some("source"))),
      "combined material bytes" -> (_input.copy(review = Some(_context.copy(changedPaths = (1 to 5).map(i => s"$i.scala").toVector))),
        (1 to 5).map(i => s"$i.scala" -> Some("x" * 220000)).toMap),
      "material vector" -> (_input.copy(review = Some(_context.copy(changedPaths = (1 to 64).map(i => s"$i.scala").toVector))),
        (1 to 64).map(i => s"$i.scala" -> Some("source")).toMap),
      "requirement bytes" -> (_input.copy(purpose = "r" * 1048577), Map("one.scala" -> Some("source"))))
    boundedCases.foreach { (name, data) =>
      s"B2-14 reject oversized $name without provider invocation" in {
        Given(s"selected actual $name exceeding the CBD request bound")
        val (input, files) = data
        val port = new RecordingPort(files)
        When("accept constructs its complete material and requirement request")
        val result = _run(port, input)
        Then("the request fails without truncation, scope dropping or provider calls")
        result.isSuccess shouldBe false
        port.requests shouldBe empty
      }
    }

    "B2-15 reject a requirement vector above 64 entries" in {
      Given("saved usage and 64 applicable acceptance descriptions alongside the assigned purpose")
      val effective = GoalPhaseAcceptanceBoundary.Effective(Some("actual usage"), (1 to 64).map { i =>
        GoalPhaseAcceptanceBoundary.EffectiveCondition(s"condition-$i", "step", true, s"description-$i", "project")
      }.toVector)
      val input = _input.copy(selectedPlan = _input.selectedPlan.copy(effectiveAcceptance = Some(effective)))
      val port = new RecordingPort()
      When("accept constructs the complete requirement vector")
      val result = _run(port, input)
      Then("the whole request fails before provider invocation without discarding conditions")
      result.isSuccess shouldBe false
      port.requests shouldBe empty
    }

    "B2-16 fail normal production acquisition without an owning component or subsystem" in {
      Given("actual ActionCall.Core fixtures with a current context and either no component or an unattached component")
      val context = ExecutionContext.create()
      val absent = ActionCall.Core(null, context, None, None)
      val unattached = ActionCall.Core(null, context, Some(new SmWorkflowComponent()), None)
      When("the production entry attempts accept through its owning subsystem")
      val missingComponent = admitC(absent, _input, _submitted())
      val missingSubsystem = admitC(unattached, _input, _submitted())
      Then("both fail without choosing any global or fixture provider")
      missingComponent.isSuccess shouldBe false
      missingSubsystem.isSuccess shouldBe false
    }

    "B2-17 reject unknown or missing outcomes before acquisition" in {
      Given("unknown and missing submitted review outcomes")
      val port = new RecordingPort()
      When("the consumer chooses its accept-only behavior")
      val unknown = _run(port, outcome = "completed")
      val missing = admitC(_input, null, _parameters(), port)
      Then("neither outcome can authorize reads or a provider call")
      unknown.isSuccess shouldBe false
      missing.isSuccess shouldBe false
      port.reads shouldBe empty
      port.requests shouldBe empty
    }

    "B2-18 reject oversized selected metadata without provider invocation" in {
      Given("valid explicit model scope with actual metadata text exceeding the per-material UTF8 bound")
      val port = new RecordingPort(Map("one.scala" -> Some("source"), "model/metadata.json" -> Some("m" * 262145)))
      When("accept acquires the configured metadata through the same logical File port")
      val result = _run(port, parameters = _model())
      Then("the complete request fails without dropping metadata or invoking the provider")
      result.isSuccess shouldBe false
      port.reads shouldBe Vector("one.scala", "model/metadata.json")
      port.requests shouldBe empty
    }

    "B2-19 reject an invalid submitted review identity before acquisition" in {
      Given("an accept disposition with an oversized actual result-reference identity")
      val submitted = _submitted().copy(resultReference = ContextReference("r" * 129, "7"))
      val port = new RecordingPort()
      When("the consumer selects the ordinary review reference")
      val result = admitC(_input, submitted, _parameters(), port)
      Then("the CBD identity bound fails before any file or provider effect")
      result.isSuccess shouldBe false
      port.reads shouldBe empty
      port.requests shouldBe empty
    }
  }
}
