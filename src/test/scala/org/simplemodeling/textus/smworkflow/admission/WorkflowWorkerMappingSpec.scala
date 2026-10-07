package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowWorkerMappingSpec extends AnyWordSpec with Matchers {
  import WorkflowWorkerMapping.*

  private val inputCodec = codec("FixtureInput")
  private val resultCodec = codec("FixtureResult")
  private def codec(identity: String): WorkflowResultJsonV1.PayloadCodec[String] =
    new WorkflowResultJsonV1.PayloadCodec[String] {
      val typeIdentity = identity
      def encode(value: String): Json = Json.fromString(value)
      def decode(value: Json): Either[String, String] = value.asString.toRight("Expected a fixture string")
    }
  private val handle = WorkflowHandle(SmWorkflowComponent.componentId,
    WorkflowDefinitionIdentity("FixtureWorkflow"), WorkflowDefinitionRevision("fixture-v1"),
    InstanceIdentity("fixture-instance"))
  private val fact = ContextReference("completed", "1")
  private val evidence = ContextReference("review-receipt", "1")
  private val context = ContextBundle("Review fixture", Vector.empty, Vector.empty,
    ContextSnapshot("fixture-v1", workspaceRevision = Some("workspace-1")))
  private val request = ContinuationRequest(StateMachineRunIdentity("run-1"),
    ContinuationIdentity("continuation-1"), StateMachineRevision("2"),
    StateMachineRequiredOperationIdentity("review"), StateMachineOperationIdentity("FixtureService", "review"),
    Some(TypedValue("FixtureInput", "candidate")), Some(StateMachineResultTypeReference("FixtureResult")),
    context, CompletionContract("completion", Vector(fact)), EvidenceContract("evidence", Vector(evidence)),
    StateMachineOperationIdentity("FixtureService", "submitReview"))
  private val requirement = ExecutionRequirement(Vector(CapabilityRequirement("review")),
    RiskLevel("standard"), ReasoningLevel.Deep, reviewRequired = true)
  private val presentation = MinimalPresentation("Review", "Waiting for review")
  private val work = WorkflowContinuation.WorkOrder(request, requirement, presentation)
  private val issued = WorkflowInteraction[String, Nothing](handle, work)
  private val profile = Profile("fixture-reviewer", "fixture-model", Set("review"), Set("standard"), true)
  private val policy = Policy("fixture-policy-v1", Vector(Mapping(ReasoningLevel.Deep, profile)))
  private val completion = SkillWorkResult(request.runId, request.continuationId, request.expectedRevision,
    context.snapshot, TypedValue("FixtureResult", "accept"), ContextReference("result", "1"),
    Vector(fact), Vector(evidence), Some("fixture-host-worker"), Some(profile.modelIdentity),
    profile.identity, policy.version)
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Worker mapping rejected: $failure")
  }
  private def select(value: Policy = policy) = take(selectC(issued, value, inputCodec))

  "Workflow worker mapping" should {
    "map every abstract ReasoningLevel through a Host-owned policy without altering the WorkOrder" in {
      val all = Policy("all-levels-v1", ReasoningLevel.values.toVector.map(level =>
        Mapping(level, profile.copy(identity = s"fixture-${level.value}", modelIdentity = s"model-${level.value}"))))
      ReasoningLevel.values.foreach { level =>
        val selected = issued.copy(current = work.copy(requirement = requirement.copy(reasoning = level)))
        val dispatch = take(selectC(selected, all, inputCodec))
        dispatch.issued shouldBe selected
        dispatch.profile.identity shouldBe s"fixture-${level.value}"
        dispatch.policy shouldBe all
      }
    }

    "reject incomplete and ambiguous policies without a fallback" in {
      val invalid = Vector[Policy](null, policy.copy(version = " "), policy.copy(mappings = null),
        policy.copy(mappings = Vector.empty), policy.copy(mappings = policy.mappings ++ policy.mappings),
        policy.copy(mappings = Vector(null)), policy.copy(mappings = Vector(Mapping(null, profile))),
        policy.copy(mappings = Vector(Mapping(ReasoningLevel.Deep, null))))
      invalid.foreach(value => selectC(issued, value, inputCodec) shouldBe a[Consequence.Failure[?]])
      val badProfiles = Vector(profile.copy(identity = ""), profile.copy(modelIdentity = " "),
        profile.copy(capabilities = null), profile.copy(capabilities = Set(null)),
        profile.copy(risks = null), profile.copy(risks = Set.empty), profile.copy(risks = Set(" ")))
      badProfiles.foreach(value => selectC(issued, Policy("bad-profile", Vector(Mapping(ReasoningLevel.Deep, value))),
        inputCodec) shouldBe a[Consequence.Failure[?]])
      selectC(issued, policy.copy(mappings = Vector(Mapping(ReasoningLevel.Standard, profile))),
        inputCodec) shouldBe a[Consequence.Failure[?]]
    }

    "require capability, risk and review compatibility" in {
      Vector(profile.copy(capabilities = Set.empty), profile.copy(risks = Set("other")),
        profile.copy(reviewCapable = false)).foreach { value =>
        selectC(issued, Policy("incompatible", Vector(Mapping(ReasoningLevel.Deep, value))),
          inputCodec) shouldBe a[Consequence.Failure[?]]
      }
      selectC(issued.copy(current = work.copy(requirement = requirement.copy(
        capabilities = requirement.capabilities ++ requirement.capabilities))), policy,
        inputCodec) shouldBe a[Consequence.Failure[?]]
      selectC(issued.copy(current = work.copy(requirement = requirement.copy(reviewRequired = false))),
        Policy("non-review", Vector(Mapping(ReasoningLevel.Deep, profile.copy(reviewCapable = false)))),
        inputCodec).isSuccess shouldBe true
    }

    "reject malformed projections and non-WorkOrder continuations through the common codec" in {
      val invalid = Vector[WorkflowInteraction[String, Nothing]](null, issued.copy(handle = null),
        issued.copy(current = null), issued.copy(current = work.copy(request = null)),
        issued.copy(current = work.copy(requirement = null)), issued.copy(current = work.copy(presentation = null)),
        issued.copy(current = work.copy(request = request.copy(input = Some(TypedValue("WrongInput", "candidate"))))),
        issued.copy(current = work.copy(request = request.copy(
          context = context.copy(snapshot = ContextSnapshot("stale"))))),
        issued.copy(current = work.copy(request = request.copy(input = Some(TypedValue("FixtureInput", null))))),
        issued.copy(current = WorkflowContinuation.Decision(request, presentation)),
        issued.copy(current = WorkflowContinuation.Wait(request.expectedRevision, context.snapshot, presentation)))
      invalid.foreach(value => selectC(value, policy, inputCodec) shouldBe a[Consequence.Failure[?]])
      selectC(issued, policy, null) shouldBe a[Consequence.Failure[?]]
    }

    "normalize only completed Host evidence and round-trip the common Result" in {
      val dispatch = select()
      val normalized = take(normalizeC(dispatch, completion))
      normalized.handle shouldBe issued.handle
      normalized.result shouldBe completion.result
      normalized.evidence shouldBe ExecutionEvidence(Vector(evidence), completion.workerIdentity,
        completion.modelIdentity, Some(SkillDispatchEvidence(requirement, profile.identity, policy.version)))
      take(WorkflowResultJsonV1.decodeC(take(WorkflowResultJsonV1.encodeC(normalized, resultCodec)),
        resultCodec)) shouldBe normalized
    }

    "reject wrong profiles, policy versions and unattested worker or model identities" in {
      val dispatch = select()
      Vector[SkillWorkResult[String]](null, completion.copy(selectedWorkerProfile = "other"),
        completion.copy(mappingPolicyVersion = "other"), completion.copy(workerIdentity = None),
        completion.copy(workerIdentity = null), completion.copy(workerIdentity = Some(" ")),
        completion.copy(modelIdentity = None), completion.copy(modelIdentity = null),
        completion.copy(modelIdentity = Some("other"))).foreach { value =>
        normalizeC(dispatch, value) shouldBe a[Consequence.Failure[?]]
      }
      normalizeC[String, String](null, completion) shouldBe a[Consequence.Failure[?]]
    }

    "preserve issued-work admission for stale identity, context, type and incomplete evidence" in {
      val dispatch = select()
      Vector(completion.copy(runId = StateMachineRunIdentity("other")),
        completion.copy(continuationId = ContinuationIdentity("other")),
        completion.copy(expectedRevision = StateMachineRevision("stale")),
        completion.copy(contextSnapshot = context.snapshot.copy(workspaceRevision = Some("stale"))),
        completion.copy(result = TypedValue("WrongResult", "accept")),
        completion.copy(result = TypedValue("FixtureResult", null)),
        completion.copy(completionFacts = Vector.empty), completion.copy(evidenceReferences = Vector.empty),
        completion.copy(completionFacts = null), completion.copy(evidenceReferences = null)).foreach { value =>
        normalizeC(dispatch, value) shouldBe a[Consequence.Failure[?]]
      }
    }

    "freeze selections across policy replacement and leave deterministic admission unchanged" in {
      val oldDispatch = select()
      val replacement = Policy("fixture-policy-v2", Vector(Mapping(ReasoningLevel.Deep,
        profile.copy(identity = "fixture-other-reviewer", modelIdentity = "fixture-other-model"))))
      val newDispatch = select(replacement)
      oldDispatch.issued shouldBe newDispatch.issued
      val oldResult = take(normalizeC(oldDispatch, completion))
      val otherCompletion = completion.copy(selectedWorkerProfile = newDispatch.profile.identity,
        modelIdentity = Some(newDispatch.profile.modelIdentity), mappingPolicyVersion = replacement.version)
      val newResult = take(normalizeC(newDispatch, otherCompletion))
      newResult.copy(evidence = oldResult.evidence) shouldBe oldResult
      normalizeC(oldDispatch, otherCompletion) shouldBe a[Consequence.Failure[?]]
      normalizeC(newDispatch, completion) shouldBe a[Consequence.Failure[?]]
      val deterministic = oldResult.copy(evidence = ExecutionEvidence(Vector(evidence)))
      admitResultC(issued, deterministic).isSuccess shouldBe true
      deterministic.evidence.skillDispatch shouldBe None
      deterministic.evidence.workerIdentity shouldBe None
      deterministic.evidence.modelIdentity shouldBe None
      take(normalizeC(oldDispatch, completion)) shouldBe oldResult
    }
  }
}
