package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, StateMachineOperationIdentity, WorkflowInstancePersistence}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseReasoningSemanticRequest as GeneratedRequest,
  SplitPhaseReasoningSemanticResult as GeneratedResult}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitReasoningSemantic as R,
  SplitPhaseExplicitReasoningSemanticJson as J, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitBoundaryValidation as B,
  SplitPhaseExplicitObjectiveSelection as O}
import SplitPhaseExplicitPartitionFixture.take

/*
 * @since   Oct.  5, 2026
  * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class SplitPhaseExplicitReasoningDefinitionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:split-phase-reasoning-interface, example:E1, rules:R1, phase:2")
  private val _e2 = afterWord("in spec:split-phase-reasoning-interface, example:E2, rules:R2, phase:2")
  private val _e3 = afterWord("in spec:split-phase-reasoning-interface, example:E3, rules:R3, phase:2")
  private def _json(text: String): Json = parse(text).fold(error => fail(error.toString), identity)

  "E1 Reasoning operations belong to the actual generated Workflow" must _e1 {
    "retain distinct Required and Provided purpose identities" in {
      Given("Spec: docs/spec/split-phase-reasoning-interface.md; Rules: R1; Example: E1; the actual generated Workflow and producer")
      val candidate = take(SplitPhaseDefinitionBinding.candidateC)

      When("the reasoning capability and its declared completion are selected from the admitted definition")
      val operation = take(SplitPhaseDefinitionBinding.reasoningSemanticOperationC)
      val completion = SplitPhaseDefinitionBinding.providedDefinition.providedOperations.filter(_.name == "submitReasoningResult")
      val judgment = candidate.sidecar.models.head.judgments.find(_.identity.value == "AssessReasoningDemand").get

      Then("the actual result type is declared for completion without changing the JSON carrier")
      candidate.workflow.version shouldBe "split-phase-business-operation-v14"
      operation.identity.capability shouldBe "assess-reasoning-demand-capability"
      operation.actionIdentity shouldBe "AssessReasoningDemand"
      operation.operation shouldBe StateMachineOperationIdentity("SplitSemanticService", "assessReasoningDemand")
      operation.inputType.map(_.value) shouldBe Some(J.requestCodec.typeIdentity)
      operation.resultType.map(_.value) shouldBe Some(J.resultCodec.typeIdentity)
      completion.size shouldBe 1
      completion.head.service shouldBe "SplitPhaseService"
      completion.head.inputType shouldBe Some(J.resultCodec.typeIdentity)
      completion.head.resultType shouldBe Some("SplitPhaseReasoningSubmissionResult")
      judgment.alternatives.map(_.value) shouldBe Vector("Enriched", "DecisionNeeded")
      judgment.criteria.map(_.value) shouldBe Vector("exact-ordered-unknown-item-coverage",
        "preserve-known-demand-closure-and-authority", "classification-rationale-and-evidence-only",
        "no-participant-policy-routing-or-record-selection")
      judgment.evidenceProvenance.value shouldBe "workflow-owned-result-reference"
      val required = candidate.workflow.requiredSpi.find(_.capability == operation.identity.capability).get
      val action = candidate.workflow.actions.find(_.identity == operation.actionIdentity).get
      required.operation shouldBe action.operation
    }
  }

  "E2 Reasoning application payloads match actual generated raw value classes" must _e2 {
    "preserve original closure and classification data without a schema adapter" in {
      Given("Spec: docs/spec/split-phase-reasoning-interface.md; Rules: R2; Example: E2; structural raw data, not native-issued work or semantic admission")
      val authority = P.AuthorityReference("original-authority", "1")
      val items = Vector(1, 2).map(index => {
        val id = P.WorkItemIdentity(s"item-$index")
        R.UnknownItem(id, s"unknown-$index", B.Item(id, Vector(B.ClosureCriterion(s"complete-$index")),
          Vector(P.WorkItemIdentity("item-0")), authority))
      })
      val request = R.Request(S.ContextReference("context", "1"), P.SourceReference("source", "1"), authority,
        O.DemandReference("original-demand", "1"), items, S.ResultReference("assigned-reasoning-result", "1"))
      val enriched = R.Judgment(R.Decision.Enriched, "Classify the original unknown work",
        items.zip(R.Classification.values.toVector).map { (item, classification) =>
          R.Assignment(item.identity, classification, "Original closure classification")
        }, Vector(S.EvidenceReference("source-analysis", "1")))
      val values = Vector(enriched, enriched.copy(decision = R.Decision.DecisionNeeded, assignments = Vector.empty))
      val requestjson = take(J.requestCodec.encodeC(request)).hcursor.downField("payload").focus.get
      val resultjson = values.map(value => take(J.resultCodec.encodeC(value)).hcursor.downField("payload").focus.get)

      When("the unchanged application payloads decode directly into the CML-generated value classes")
      val generatedrequest = requestjson.as[GeneratedRequest].fold(error => fail(error.toString), identity)
      val generatedresults = resultjson.map(_.as[GeneratedResult].fold(error => fail(error.toString), identity))

      Then("all declared data round trips while participant execution controls remain unrepresentable")
      generatedrequest.asJson shouldBe requestjson
      generatedresults.map(_.asJson) shouldBe resultjson
      generatedrequest.productElementNames.toVector shouldBe Vector("context", "source", "authority", "demand", "items", "result")
      generatedrequest.result.identity shouldBe request.result.identity
      generatedrequest.result.revision shouldBe request.result.revision
      generatedresults.head.productElementNames.toVector shouldBe Vector("decision", "rationale", "assignments", "evidence")
      generatedrequest.items.map(_.identity.value) shouldBe items.map(_.identity.value)
      generatedrequest.items.head.closure.authority.identity shouldBe authority.identity
      generatedrequest.items.head.closure.prerequisiteItems.head.value shouldBe "item-0"
      generatedresults.head.assignments.map(_.classification) shouldBe Vector("ExpensiveReasoning", "SettledExecution")
      generatedresults.last.assignments shouldBe empty
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.value.SplitPhaseReasoningSemanticResult(\"Enriched\", \"rationale\", Vector.empty, Vector.empty, nextOperation = \"apply\")")
    }
  }

  "E3 The reasoning definition does not implicitly migrate saved v8 or v9" must _e3 {
    Vector("split-phase-human-terminal-v8", "split-phase-reasoning-demand-v9").foreach { oldrevision =>
      s"reject independently admitted $oldrevision resources instead of rebinding their version" in {
        Given("Spec: docs/spec/split-phase-reasoning-interface.md; Rules: R3; Example: E3; complete official resources with mutually consistent selected old revision labels")
        val workflow = _json(Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json")))
        val producer = _json(Files.readString(Path.of("target/cozy/candidate-admission-producer-abi.json")))
        val wi = workflow.hcursor.downField("workflows").focus.get.asArray.get.indexWhere(
          _.hcursor.get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
        val pi = producer.hcursor.downField("models").focus.get.asArray.get.indexWhere(
          _.hcursor.downField("model").downField("workflow").get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
        wi should be >= 0
        pi should be >= 0
        val oldworkflow = workflow.hcursor.downField("workflows").downN(wi).downField("version")
          .withFocus(_ => Json.fromString(oldrevision)).top.get.noSpaces
        val oldproducer = producer.hcursor.downField("models").downN(pi).downField("model").downField("workflow")
          .downField("version").withFocus(_ => Json.fromString(oldrevision)).top.get.noSpaces
        val old = take(CandidateWorkflowAbi.parseSelectedC(oldworkflow, take(CandidateAdmissionProducerAbi.parseC(oldproducer)),
          "src/main/cozy/sm-workflow.cml", "SplitPhaseWorkflow", oldrevision))
        val oldbinding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(old))

        When("the receiver-valid old binding and resources meet the current compiled catalog")
        val current = take(SplitPhaseDefinitionBinding.bindC)
        val result = SplitPhaseRuntimeCatalog.parseC(oldworkflow, oldproducer)

        Then("the old label remains incompatible data and cannot silently become the new reasoning definition")
        oldbinding.workflowRevision.value shouldBe oldrevision
        current.workflowRevision.value shouldBe "split-phase-business-operation-v14"
        result shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
