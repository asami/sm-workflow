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

/**
 * @since   Sep. 28, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowWorkerMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
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

    "reject unavailable selections without a fallback" in {
      Given("workflow-worker-result-consumption R1: selection needs an accessible ordered table and a usable matching row")
      val invalid = Vector[Policy](null, policy.copy(mappings = null),
        policy.copy(mappings = Vector.empty),
        policy.copy(mappings = Vector(null)), policy.copy(mappings = Vector(Mapping(null, profile))),
        policy.copy(mappings = Vector(Mapping(ReasoningLevel.Deep, null))),
        policy.copy(mappings = Vector(Mapping(ReasoningLevel.Standard, profile))))
      val unusable = Vector(profile.copy(capabilities = null), profile.copy(capabilities = Set(null)),
        profile.copy(risks = null), profile.copy(risks = Set.empty), profile.copy(risks = Set(" ")))
      When("lookup receives missing selections or profiles unable to fulfill this requirement")
      val rejected = invalid.map(value => selectC(issued, value, inputCodec)) ++
        unusable.map(value => selectC(issued,
          Policy("bad-profile", Vector(Mapping(ReasoningLevel.Deep, value))), inputCodec))
      Then("selection fails without returning an unusable dispatch")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "ignore unused policy rows and diagnostics at the ordered selection boundary" in {
      Given("workflow-worker-result-consumption R1: version and unused rows neither execute work nor grant progression")
      val diagnostic = profile.copy(identity = "", modelIdentity = " ",
        capabilities = profile.capabilities ++ Set(null, " "), risks = profile.risks ++ Set(null, " "))
      val unused = Vector[Mapping](null, Mapping(null, null), Mapping(ReasoningLevel.Standard, null),
        Mapping(ReasoningLevel.Standard, profile.copy(capabilities = null)),
        Mapping(ReasoningLevel.Deep, profile.copy(identity = "later", modelIdentity = "later-model")))
      val decorated = Policy(null, Vector(Mapping(ReasoningLevel.Deep, diagnostic)) ++ unused)
      When("the existing first matching row is selected despite unused entries and metadata")
      val selected = take(selectC(issued, decorated, null))
      val repeated = take(selectC(issued, policy.copy(version = " ",
        mappings = policy.mappings ++ policy.mappings), inputCodec))
      Then("only the actual selected row supplies dispatch and issued work is not progressed")
      selected.profile shouldBe diagnostic
      selected.policy shouldBe decorated
      selected.issued shouldBe issued
      repeated.profile shouldBe profile
    }

    "not search for a compatible fallback after the first matching row" in {
      Given("workflow-worker-result-consumption R1: ordered lookup selects one declared profile, not any possible worker")
      val unsuitable = profile.copy(capabilities = Set.empty)
      val ordered = Policy("fixture", Vector(Mapping(ReasoningLevel.Deep, unsuitable),
        Mapping(ReasoningLevel.Deep, profile)))
      When("the selected row cannot fulfill the issued capability despite a later compatible row")
      val rejected = selectC(issued, ordered, inputCodec)
      Then("the operation refuses the selected unusable dispatch rather than silently choosing another executor")
      rejected shouldBe a[Consequence.Failure[?]]
    }

    "require capability, risk and review compatibility" in {
      Given("workflow-worker-result-consumption R1: profile selection consumes actual capability, risk and review requirements")
      val incompatible = Vector(profile.copy(capabilities = Set.empty), profile.copy(risks = Set("other")),
        profile.copy(reviewCapable = false))
      val repeated = issued.copy(current = work.copy(requirement = requirement.copy(
        capabilities = requirement.capabilities ++ requirement.capabilities)))
      When("selection consumes incompatible profiles, repeated required capabilities and work without a review requirement")
      val rejected = incompatible.map(value => selectC(issued,
        Policy("incompatible", Vector(Mapping(ReasoningLevel.Deep, value))), inputCodec))
      val selected = selectC(repeated, policy, inputCodec)
      val withoutreview = selectC(issued.copy(current = work.copy(requirement = requirement.copy(reviewRequired = false))),
        Policy("non-review", Vector(Mapping(ReasoningLevel.Deep, profile.copy(reviewCapable = false)))),
        inputCodec)
      Then("incompatible execution requirements reject, but repeating a capability creates no new restriction")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      take(selected).profile shouldBe profile
      withoutreview.isSuccess shouldBe true
    }

    "consume the selection requirement rather than certify a whole WorkOrder projection" in {
      Given("workflow-worker-result-consumption R1: selection uses reasoning, capabilities, risk and review, not input wire or diagnostics")
      val irrelevant = Vector(issued.copy(handle = null), issued.copy(current = work.copy(request = null)),
        issued.copy(current = work.copy(presentation = null)),
        issued.copy(current = work.copy(request = request.copy(input = Some(TypedValue("WrongInput", "candidate"))))),
        issued.copy(current = work.copy(request = request.copy(context = context.copy(snapshot = ContextSnapshot("stale"))))),
        issued.copy(current = work.copy(request = request.copy(input = Some(TypedValue("FixtureInput", null))))))
      val invalid = Vector[WorkflowInteraction[String, Nothing]](null, issued.copy(current = null),
        issued.copy(current = work.copy(requirement = null)),
        issued.copy(current = WorkflowContinuation.Decision(request, presentation)),
        issued.copy(current = WorkflowContinuation.Wait(request.expectedRevision, context.snapshot, presentation)))
      When("selection receives unused projection differences and inputs with no consumable WorkOrder requirement")
      val selected = irrelevant.map(value => selectC(value, policy, null))
      val rejected = invalid.map(value => selectC(value, policy, inputCodec))
      Then("unused projection and codec values grant no progression and do not obstruct compatible profile selection")
      selected.foreach(value => take(value).profile shouldBe profile)
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
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

    "translate returned worker diagnostics without attesting a frozen selection" in {
      Given("workflow-worker-result-consumption R2: a selected policy and representable returned diagnostics, not execution authority")
      val dispatch = select()
      val variants = Vector(completion.copy(selectedWorkerProfile = "other"),
        completion.copy(selectedWorkerProfile = " "), completion.copy(mappingPolicyVersion = ""),
        completion.copy(mappingPolicyVersion = "other"), completion.copy(workerIdentity = None),
        completion.copy(workerIdentity = Some(" ")), completion.copy(modelIdentity = None),
        completion.copy(modelIdentity = Some("other")))
      When("normalization converts those values without selecting or executing a worker")
      val converted = variants.map(value => value -> normalizeC(dispatch, value))
      val missingcompletion = normalizeC[String, String](dispatch, null)
      val missingdispatch = normalizeC[String, String](null, completion)
      Then("the returned diagnostic values are preserved and only unavailable conversion inputs fail")
      converted.foreach { case (submitted, outcome) =>
        val result = take(outcome)
        result.result shouldBe submitted.result
        result.evidence.workerIdentity shouldBe submitted.workerIdentity
        result.evidence.modelIdentity shouldBe submitted.modelIdentity
        result.evidence.skillDispatch shouldBe Some(SkillDispatchEvidence(requirement,
          submitted.selectedWorkerProfile, submitted.mappingPolicyVersion))
      }
      missingcompletion shouldBe a[Consequence.Failure[?]]
      missingdispatch shouldBe a[Consequence.Failure[?]]
    }

    "leave native target and condition admission to the consuming completion" in {
      Given("workflow-worker-result-consumption R2: normalization is not an authority or native completion boundary")
      val dispatch = select()
      val variants = Vector(completion.copy(runId = StateMachineRunIdentity("other")),
        completion.copy(continuationId = ContinuationIdentity("other")),
        completion.copy(expectedRevision = StateMachineRevision("stale")),
        completion.copy(contextSnapshot = context.snapshot.copy(workspaceRevision = Some("stale"))),
        completion.copy(result = TypedValue("WrongResult", "accept")),
        completion.copy(completionFacts = Vector.empty), completion.copy(evidenceReferences = Vector.empty))
      When("returned target, type, diagnostics and evidence values are converted without a transition")
      val converted = variants.map(value => value -> normalizeC(dispatch, value))
      Then("conversion preserves even values that a later native completion must reject")
      converted.foreach { case (submitted, outcome) =>
        val result = take(outcome)
        result.runId shouldBe submitted.runId
        result.continuationId shouldBe submitted.continuationId
        result.expectedRevision shouldBe submitted.expectedRevision
        result.contextSnapshot shouldBe submitted.contextSnapshot
        result.result shouldBe submitted.result
        result.completionFacts shouldBe submitted.completionFacts
        result.evidence.references shouldBe submitted.evidenceReferences
      }
    }

    "retain policy selections without comparing a completion to their copied diagnostics" in {
      Given("workflow-worker-result-consumption R1 R2: old and replacement policies choose profiles, not progression permission")
      val oldDispatch = select()
      val replacement = Policy("fixture-policy-v2", Vector(Mapping(ReasoningLevel.Deep,
        profile.copy(identity = "fixture-other-reviewer", modelIdentity = "fixture-other-model"))))
      val newDispatch = select(replacement)
      val otherCompletion = completion.copy(selectedWorkerProfile = newDispatch.profile.identity,
        modelIdentity = Some(newDispatch.profile.modelIdentity), mappingPolicyVersion = replacement.version)
      When("both selections convert either returned diagnostic profile without executing a worker")
      val oldResult = take(normalizeC(oldDispatch, completion))
      val newResult = take(normalizeC(newDispatch, otherCompletion))
      val oldwithnew = normalizeC(oldDispatch, otherCompletion)
      val newwithold = normalizeC(newDispatch, completion)
      val deterministic = oldResult.copy(evidence = ExecutionEvidence(Vector(evidence)))
      val admitted = admitResultC(issued, deterministic)
      Then("policy selection stays original while returned data neither certifies nor reselects execution")
      oldDispatch.profile shouldBe profile
      newDispatch.profile.identity shouldBe "fixture-other-reviewer"
      oldResult.result shouldBe newResult.result
      oldResult.evidence.skillDispatch.get.mappingPolicyVersion shouldBe policy.version
      newResult.evidence.skillDispatch.get.mappingPolicyVersion shouldBe replacement.version
      oldwithnew shouldBe Consequence.success(newResult)
      newwithold shouldBe Consequence.success(oldResult)
      admitted.isSuccess shouldBe true
      deterministic.evidence.skillDispatch shouldBe None
      deterministic.evidence.workerIdentity shouldBe None
      deterministic.evidence.modelIdentity shouldBe None
    }
  }
}
