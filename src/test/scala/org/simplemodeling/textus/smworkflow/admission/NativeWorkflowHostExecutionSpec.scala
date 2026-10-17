package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.host.NativeWorkflowHostExecution as P
import org.simplemodeling.textus.smworkflow.value as V
import WorkflowExecutionHarness as H

/** Actual public WorkOrder projection with specification-owned host observations.
  * This library fixture does not install a provider or establish two real chats.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeWorkflowHostExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _profile = WorkflowConcreteExecutionProfile("fixture-profile", "fixture-provider", "fixture-model", Map.empty)
  private val _origin = WorkflowExecutorDescriptor("fixture-origin", _profile)
  private val _reference = V.WorkflowArtifactReference("fixture-result", "1")
  private val _codec = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = "FixtureSemanticInput"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("Expected fixture text")
  }
  private def _take[A](value: Consequence[A]): A = value.toOption.get
  private val _mapping = _take(WorkflowExecutionProfileMapping.createC("fixture-mapping", "1", Vector(_profile),
    Vector(WorkflowExecutionProfileMapping.ClassBinding("FixtureClass", _profile.identity))))
  private val _handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("fixture-workflow"),
    WorkflowDefinitionRevision("1"), InstanceIdentity("fixture-instance"))
  private val _context = ContextBundle("Assigned fixture scope", Vector.empty, Vector.empty, ContextSnapshot(_handle.workflowRevision.value, None, None, None))
  private val _request = ContinuationRequest(StateMachineRunIdentity("fixture-run"), ContinuationIdentity("fixture-next"),
    StateMachineRevision("7"), StateMachineRequiredOperationIdentity("fixture-semantic"), StateMachineOperationIdentity("Fixture", "work"),
    Some(TypedValue(_codec.typeIdentity, "private input")), Some(StateMachineResultTypeReference("FixtureResult")), _context,
    CompletionContract("fixture-completion", Vector.empty), EvidenceContract("fixture-evidence", Vector.empty),
    StateMachineOperationIdentity("Fixture", "submit"))
  private def _issued(mode: NativeWorkflowIssuedWorkContext.Mode = NativeWorkflowIssuedWorkContext.Mode.Implementation,
    disposition: Boolean = true): V.WorkflowIssuedWorkContext = {
    val requirement = ExecutionRequirement(Vector(CapabilityRequirement("fixture-semantic")), RiskLevel("bounded"),
      ReasoningLevel.Standard, reviewRequired = true, if (disposition) Some(ExecutionDisposition(
        ExecutionPlacement.Delegated, ExecutionIndependence.Required, _origin.contextIdentity)) else None,
      Some(ReasoningClassReference("FixtureClass")))
    val work = WorkflowInteraction[String, Nothing](_handle, WorkflowContinuation.WorkOrder(_request, requirement,
      MinimalPresentation("Assigned fixture work", "Pending")))
    _take(NativeWorkflowIssuedWorkContext.fromIssuedC(work, mode, _codec))
  }
  private def _completed(prepared: P.Prepared): P.Prepared = _take(prepared.recordC(prepared.session.nextSelection.get,
    H.Outcome(H.Status.Completed, H.Reason.Completed, "Observed specification executor returned assigned work",
      Some(WorkflowExecutorDescriptor("fixture-independent", _profile.copy(identity = "actual-profile-alias"))),
      H.Resources(elapsedMillis = Some(25), machineIdentity = Some("fixture-machine")))))

  "Native host execution bridge" should afterWord("in spec:native-host-execution, examples:E1,E2,E3,E4, rules:R1,R2,R3,R4,R5, phase:6") {
    "E1 retain all four actual public scopes through host preparation restoration and completion evidence" in {
      for (mode <- NativeWorkflowIssuedWorkContext.Mode.values.toVector) {
        Given("R1,R3,R4: the actual generated public " + mode.value + " WorkOrder context and canonical original mapping")
        val issued = _issued(mode)
        When("the host prepares, records its specification-owned actual descriptor and restores the generic artifact")
        val prepared = _take(P.prepareC(issued, _mapping, _origin))
        val completed = _completed(prepared)
        val restored = _take(P.restoreC(issued, _take(H.decodeC(_take(H.encodeC(completed.session))))))
        val evidence = _take(restored.completionEvidenceC(Vector(_reference)))
        Then("the original mode scope and completion route remain selected while evidence projects the actual executor and model")
        restored.issued shouldBe issued
        restored.issued.thinkingMode shouldBe mode.value
        restored.issued.context shouldBe issued.context
        restored.issued.completionOperation shouldBe issued.completionOperation
        restored.issued.resultType shouldBe Some("FixtureResult")
        restored.session.workReference.handle shouldBe _handle
        restored.session.workReference.expectedRevision shouldBe StateMachineRevision("7")
        restored.session.attempts.last.outcome.resources.inputTokens shouldBe None
        restored.session.attempts.last.outcome.resources.elapsedMillis shouldBe Some(25)
        evidence.references shouldBe Vector(_reference)
        evidence.workerIdentity shouldBe Some("fixture-independent")
        evidence.modelIdentity shouldBe Some("fixture-model")
        evidence.execution.get shouldBe V.WorkflowExecutionObservation("DELEGATED", "fixture-origin", "fixture-independent", "actual-profile-alias")
      }
    }

    "E2 reject unavailable mapped inputs and contradictory consumed work references" in {
      Given("R1,R4: an original prepared session and missing unsupported or contradictory issued fields")
      val issued = _issued()
      val prepared = _take(P.prepareC(issued, _mapping, _origin))
      val invalid = Vector(issued.copy(runId = ""), issued.copy(continuationId = null), issued.copy(expectedRevision = ""),
        issued.copy(handle = Record.data("instanceIdentity" -> "fixture-instance")), issued.copy(schemaVersion = "other"),
        issued.copy(thinkingMode = "Human"), issued.copy(thinkingMode = "Wait"), issued.copy(thinkingMode = "Terminal"), _issued(disposition = false))
      When("preparation consumes those inputs and restoration is given mismatched native correlation")
      val failures = invalid.map(value => P.prepareC(value, _mapping, _origin))
      val mismatches = Vector(issued.copy(runId = "foreign-run"), issued.copy(continuationId = "foreign-next"),
        issued.copy(expectedRevision = "8"), issued.copy(handle = Record.data("componentIdentity" -> SmWorkflowComponent.componentId.name,
          "workflowIdentity" -> "fixture-workflow", "workflowRevision" -> "1", "instanceIdentity" -> "foreign-instance",
          "protocolVersion" -> WorkflowProtocolV1.schemaVersion)), _issued(disposition = false))
        .map(value => P.restoreC(value, prepared.session))
      Then("each consumed contradiction fails and no missing original mapping or session is synthesized")
      failures.foreach(_ shouldBe a[Consequence.Failure[?]])
      mismatches.foreach(_ shouldBe a[Consequence.Failure[?]])
      P.prepareC(issued, null, _origin) shouldBe a[Consequence.Failure[?]]
      P.restoreC(issued, null) shouldBe a[Consequence.Failure[?]]
      P.prepareC(issued, _mapping, _origin.copy(contextIdentity = "foreign-origin")) shouldBe a[Consequence.Failure[?]]
    }

    "E3 keep noncompleted operational outcomes outside semantic Submit evidence" in {
      for ((status, reason) <- Vector(H.Status.Unavailable -> H.Reason.ProviderUnavailable, H.Status.Declined -> H.Reason.CapabilityDeclined,
          H.Status.Blocked -> H.Reason.AuthorityBlocked, H.Status.Failed -> H.Reason.UncertainExecution)) {
        Given("R2,R3: actual pending public work and an observed " + status.value + " outcome")
        val prepared = _take(P.prepareC(_issued(), _mapping, _origin))
        When("the host records that outcome without inventing an executor")
        val stopped = _take(prepared.recordC(prepared.session.nextSelection.get, H.Outcome(status, reason, "Actual fixture noncompletion")))
        Then("neither pending nor stopped work produces completion evidence or another attempt")
        prepared.completionEvidenceC(Vector(_reference)) shouldBe a[Consequence.Failure[?]]
        stopped.completionEvidenceC(Vector(_reference)) shouldBe a[Consequence.Failure[?]]
        stopped.session.nextSelection shouldBe None
        stopped.issued shouldBe prepared.issued
      }
    }

    "E4 reject invalid actual descriptors and artifact references while tolerating inert scope diagnostics" in {
      Given("R3,R4: REQUIRED review with no authentication or reviewer authority supplied by completion")
      val issued = _issued()
      val prepared = _take(P.prepareC(issued, _mapping, _origin))
      val completed = _completed(prepared)
      When("same-context and wrong-profile descriptors arrive and the original scope gains unused diagnostics")
      val same = prepared.recordC(prepared.session.nextSelection.get, H.Outcome(H.Status.Completed, H.Reason.Completed, "Actual same context", Some(_origin)))
      val wrong = prepared.recordC(prepared.session.nextSelection.get, H.Outcome(H.Status.Completed, H.Reason.Completed, "Actual wrong model",
        Some(WorkflowExecutorDescriptor("distinct", _profile.copy(model = "wrong-model")))))
      val diagnostic = issued.copy(context = Record.data("summary" -> "Returned diagnostics need not attest the complete original body"))
      val restored = _take(P.restoreC(diagnostic, completed.session))
      Then("actual descriptor relations and genuine references are required while unrelated body equality is not an authority gate")
      same shouldBe a[Consequence.Failure[?]]
      wrong shouldBe a[Consequence.Failure[?]]
      completed.completionEvidenceC(Vector.empty) shouldBe a[Consequence.Failure[?]]
      completed.completionEvidenceC(Vector(_reference.copy(revision = ""))) shouldBe a[Consequence.Failure[?]]
      restored.issued shouldBe diagnostic
      _take(restored.completionEvidenceC(Vector(_reference))).workerIdentity shouldBe Some("fixture-independent")
    }
  }
}
