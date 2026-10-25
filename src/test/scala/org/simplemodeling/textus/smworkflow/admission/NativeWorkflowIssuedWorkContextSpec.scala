package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import NativeWorkflowIssuedWorkContext as P

/** Specification-owned canonical WorkOrders, never task or review evidence.
  * @since   Oct. 14, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeWorkflowIssuedWorkContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _codec(identity: String): WorkflowResultJsonV1.PayloadCodec[String] =
    new WorkflowResultJsonV1.PayloadCodec[String] {
      val typeIdentity = identity
      def encode(value: String): Json = Json.fromString(value)
      def decode(value: Json): Either[String, String] = value.asString.toRight("Expected fixture text")
    }
  private val codec = _codec("FixturePrivateInput")
  private val handle = WorkflowHandle(SmWorkflowComponent.componentId,
    WorkflowDefinitionIdentity("FixtureWorkflow"), WorkflowDefinitionRevision("fixture-v1"), InstanceIdentity("instance-1"))
  private val fact = ContextReference("fixture-fact", "3")
  private val evidence = ContextReference("fixture-evidence", "4")
  private val context = ContextBundle("Assigned fixture context", Vector(fact), Vector(evidence),
    ContextSnapshot("fixture-v1", Some("model-1"), Some("workspace-2"), Some("evidence-3")))
  private val request = ContinuationRequest(StateMachineRunIdentity("run-1"), ContinuationIdentity("continuation-2"),
    StateMachineRevision("7"), StateMachineRequiredOperationIdentity("fixture-capability"),
    StateMachineOperationIdentity("FixtureService", "perform"), Some(TypedValue(codec.typeIdentity, "private business input")),
    Some(StateMachineResultTypeReference("FixtureResult")), context, CompletionContract("completion-1", Vector(fact)),
    EvidenceContract("evidence-1", Vector(evidence)), StateMachineOperationIdentity("FixtureService", "submit"))
  private val requirement = ExecutionRequirement(Vector(CapabilityRequirement("fixture-capability")), RiskLevel("bounded"),
    ReasoningLevel.Deep, reviewRequired = true)
  private val presentation = MinimalPresentation("Private presentation", "Private situation")
  private val _order = WorkflowContinuation.WorkOrder(request, requirement, presentation)
  private val issued = WorkflowInteraction[String, Nothing](handle, _order)
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail("Issued context rejected: " + other)
  }
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:native-issued-work-context, example:$example, rules:$rules, phase:6")

  "E1 finite semantic modes" should _metadata("E1", "R1,R2,R5") {
    "retain the assigned canonical identity and contracts for each semantic mode and only supported Goal kinds" in {
      Given("one specification WorkOrder with explicit native route, revision and contracts, and the four allowed Goal kinds")
      val kinds = Vector("ImplementStep", "RepairStep", "ReviewStep", "ReviewPhase")
      When("each finite mode projects that actual WorkOrder and Goal meaning selects its assigned mode")
      val views = P.Mode.values.toVector.map(mode => _take(P.fromIssuedC(issued, mode, codec)))
      val goalmodes = kinds.map(kind => _take(P.goalModeC(kind)).value)
      Then("all four lower-case modes preserve the same canonical fields, and Goal implementation and review remain distinct")
      views.map(_.thinkingMode) shouldBe Vector("analysis", "design", "implementation", "review")
      goalmodes shouldBe Vector("implementation", "implementation", "review", "review")
      views.foreach { view =>
        view.schemaVersion shouldBe "cncf.workflow-protocol.v1"
        view.handle.getString("componentIdentity") shouldBe Some(SmWorkflowComponent.componentId.name)
        view.handle.getString("workflowIdentity") shouldBe Some("FixtureWorkflow")
        view.handle.getString("workflowRevision") shouldBe Some("fixture-v1")
        view.handle.getString("instanceIdentity") shouldBe Some("instance-1")
        view.handle.getString("protocolVersion") shouldBe Some("cncf.workflow-protocol.v1")
        view.runId shouldBe "run-1"
        view.continuationId shouldBe "continuation-2"
        view.expectedRevision shouldBe "7"
        view.requiredOperation shouldBe "fixture-capability"
        view.operation.getString("service") shouldBe Some("FixtureService")
        view.operation.getString("operation") shouldBe Some("perform")
        view.completionOperation.getString("operation") shouldBe Some("submit")
        view.context.getString("summary") shouldBe Some("Assigned fixture context")
        view.context.getRecord("snapshot").get.getString("workspaceRevision") shouldBe Some("workspace-2")
        view.completion.getString("identity") shouldBe Some("completion-1")
        view.evidence.getString("identity") shouldBe Some("evidence-1")
      }
    }
  }

  "E2 generic requirements" should _metadata("E2", "R1,R2,R6") {
    "preserve reasoning, optional semantic class and all placement/independence combinations without changing mode" in {
      Given("each native reasoning level, absent or explicit semantic class, and absent or explicit native disposition")
      val dispositions = Vector(None) ++ (for {
        placement <- ExecutionPlacement.values.toVector
        independence <- ExecutionIndependence.values.toVector
      } yield Some(ExecutionDisposition(placement, independence, "fixture-origin")))
      val requirements = for {
        level <- ReasoningLevel.values.toVector
        semanticClass <- Vector(None, Some(ReasoningClassReference("FixtureSemanticClass")))
        disposition <- dispositions
      } yield requirement.copy(reasoning = level, reasoningClass = semanticClass, disposition = disposition)
      When("the actual WorkOrders project their unchanged native execution requirements under analysis")
      val views = requirements.map(value => value -> _take(P.fromIssuedC(
        issued.copy(current = _order.copy(requirement = value)), P.Mode.Analysis, codec)))
      Then("semantic mode stays analysis and native requirement fields retain their supplied meanings and omissions")
      views.foreach { (expected, view) =>
        view.thinkingMode shouldBe "analysis"
        view.executionRequirement.getStringVector("capabilities") shouldBe Some(Vector("fixture-capability"))
        view.executionRequirement.getString("risk") shouldBe Some("bounded")
        view.executionRequirement.getString("reasoning") shouldBe Some(expected.reasoning.value)
        view.executionRequirement.getBoolean("reviewRequired") shouldBe Some(true)
        view.executionRequirement.getString("reasoningClass") shouldBe expected.reasoningClass.map(_.value)
        view.executionRequirement.getRecord("disposition").map(_.getString("placement").get) shouldBe
          expected.disposition.map(_.placement.value)
        view.executionRequirement.getRecord("disposition").map(_.getString("independence").get) shouldBe
          expected.disposition.map(_.independence.value)
        view.executionRequirement.getRecord("disposition").map(_.getString("originContextIdentity").get) shouldBe
          expected.disposition.map(_.originContextIdentity)
      }
    }
  }

  "E3 selected public data" should _metadata("E3", "R2,R3,R5") {
    "omit private payload and presentation structurally while retaining declared types and required facts" in {
      Given("a WorkOrder with private input and presentation plus explicit completion and evidence references")
      When("only the declared public context is projected to its generated Record")
      val view = _take(P.fromIssuedC(issued, P.Mode.Review, codec))
      val record = view.toRecord()
      Then("input type remains declared without private input, presentation, claim or provider authority fields")
      view.inputType shouldBe Some("FixturePrivateInput")
      view.resultType shouldBe Some("FixtureResult")
      record.keySet shouldBe Set("thinkingMode", "schemaVersion", "handle", "runId", "continuationId",
        "expectedRevision", "requiredOperation", "operation", "inputType", "resultType", "context", "completion",
        "evidence", "completionOperation", "executionRequirement")
      view.context.getVector("requiredFacts").get.map(_.asInstanceOf[org.goldenport.record.Record].getString("identity")) shouldBe
        Vector(Some("fixture-fact"))
      view.context.getVector("references").get.map(_.asInstanceOf[org.goldenport.record.Record].getString("revision")) shouldBe
        Vector(Some("4"))
      view.completion.getVector("requiredFacts").get.map(_.asInstanceOf[org.goldenport.record.Record].getString("revision")) shouldBe
        Vector(Some("3"))
      view.evidence.getVector("requiredEvidence").get.map(_.asInstanceOf[org.goldenport.record.Record].getString("identity")) shouldBe
        Vector(Some("fixture-evidence"))
    }
  }

  "E4 owning-stage failures" should _metadata("E4", "R2,R5") {
    "reject unavailable, non-WorkOrder and incompatible canonical values as Consequence failures" in {
      Given("absent inputs, non-WorkOrder continuations and WorkOrders missing consumed handle, snapshot or contracts")
      val invalid = Vector[WorkflowInteraction[String, Nothing]](null, issued.copy(handle = null), issued.copy(current = null),
        issued.copy(current = _order.copy(request = null)),
        issued.copy(current = WorkflowContinuation.Decision(request, presentation)),
        issued.copy(current = WorkflowContinuation.Wait(request.expectedRevision, context.snapshot, presentation)),
        issued.copy(current = WorkflowContinuation.Terminal[Nothing](null, presentation)),
        issued.copy(current = _order.copy(request = request.copy(input = null))),
        issued.copy(current = _order.copy(request = request.copy(context = null))),
        issued.copy(current = _order.copy(request = request.copy(context = context.copy(snapshot = null)))),
        issued.copy(current = _order.copy(request = request.copy(completion = null))),
        issued.copy(current = _order.copy(request = request.copy(evidence = null))),
        issued.copy(current = _order.copy(request = request.copy(input = Some(TypedValue("WrongInput", "private"))))))
      val throwing = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = codec.typeIdentity
        def encode(value: String): Json = throw new IllegalArgumentException("Fixture incompatible encoding")
        def decode(value: Json): Either[String, String] = Left("Unused")
      }
      When("the owning projection consumes those candidates, an absent mode/codec, or unsupported Goal kinds")
      val results = invalid.map(value => P.fromIssuedC(value, P.Mode.Design, codec)) ++ Vector(
        P.fromIssuedC(issued, null, codec), P.fromIssuedC(issued, P.Mode.Design, null),
        P.fromIssuedC(issued, P.Mode.Design, _codec("OtherInput")), P.fromIssuedC(issued, P.Mode.Design, throwing))
      val modes = Vector(null, "", "Implement", "Review", "Human", "Wait").map(P.goalModeC)
      Then("every unsupported consumed candidate fails without a fabricated context or escaping exception")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      modes.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }

  "E5 issued route fidelity" should _metadata("E5", "R2,R4,R5") {
    "retain each actual route, repeat one pending issue unchanged and leave native optional types absent" in {
      Given("a different actual instance, continuation, expected revision, result route and a canonical issue with no optional types")
      val next = issued.copy(handle = handle.copy(instanceIdentity = InstanceIdentity("instance-2")),
        current = _order.copy(request = request.copy(runId = StateMachineRunIdentity("run-2"),
          continuationId = ContinuationIdentity("continuation-3"), expectedRevision = StateMachineRevision("8"),
          resultType = Some(StateMachineResultTypeReference("OtherResult")),
          completionOperation = StateMachineOperationIdentity("OtherService", "submitOther"))))
      val untyped = issued.copy(current = _order.copy(request = request.copy[String](input = None, resultType = None)))
      When("the projection consumes these actual issues and redisplays the original pending issue")
      val first = _take(P.fromIssuedC(issued, P.Mode.Implementation, codec))
      val second = _take(P.fromIssuedC(next, P.Mode.Implementation, codec))
      val repeated = _take(P.fromIssuedC(issued, P.Mode.Implementation, codec))
      val absent = _take(P.fromIssuedC(untyped, P.Mode.Design, codec))
      Then("distinct routing stays distinct, redisplay preserves semantics and no missing native type is invented")
      second.handle.getString("instanceIdentity") shouldBe Some("instance-2")
      second.runId shouldBe "run-2"
      second.continuationId shouldBe "continuation-3"
      second.expectedRevision shouldBe "8"
      second.resultType shouldBe Some("OtherResult")
      second.completionOperation.getString("service") shouldBe Some("OtherService")
      second.completionOperation.getString("operation") shouldBe Some("submitOther")
      second should not be first
      repeated shouldBe first
      absent.inputType shouldBe None
      absent.resultType shouldBe None
    }
  }
}
