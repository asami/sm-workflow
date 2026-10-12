package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhasePartitionSemanticRequest as GeneratedRequest,
  SplitPhasePartitionSemanticResult as GeneratedResult}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPartitionSemanticJson as J, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitObjectiveSelection as O}
import SplitPhaseExplicitPartitionFixture.*
import SplitPhasePartitionEnumeration.EdgeDisposition

/**
  * @since Oct.  2, 2026
  * @version Oct. 13, 2026
  */
final class SplitPhaseExplicitPartitionDefinitionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def prepared: S.Prepared = {
    val source = base(Vector(260, 180, 260), Vector(EdgeDisposition.Unresolved, EdgeDisposition.Either))
    val records = source.records.copy(estimates = source.records.estimates.copy(items = source.records.estimates.items.updated(1,
      source.records.estimates.items(1).copy(duration = E.Duration.Unresolved("novel-work")))))
    val input = take(E.loadC(source.initial, records))
    val data = W.Data(W.DataReference("planning", "1"), source.source.reference, source.source.authority, None, O.Records(None, None, None))
    val selection = take(W.selectC(input, data))
    val pending = take(W.advanceC(selection)).asInstanceOf[W.PartitionSemanticRequired]
    take(S.prepareC(S.ContextReference("context", "1"), pending))
  }

  "Typed partition component declarations" should {
    "bind the exact converted Required and Provided Operations from the admitted generated Workflow" in {
      Given("the complete generated SplitPhase Workflow and independent producer descriptors")
      val candidate = take(SplitPhaseDefinitionBinding.candidateC)
      When("the converted capability is selected by identity rather than the historical first SPI")
      val operation = take(SplitPhaseDefinitionBinding.partitionSemanticOperationC)
      val completion = SplitPhaseDefinitionBinding.providedDefinition.providedOperations.filter(_.name == "submitPartitionSemanticResult")
      Then("request result operation and source identities belong to the actual current declarations")
      candidate.workflow.version shouldBe "split-phase-business-operation-v14"
      operation.identity.capability shouldBe "assess-partition-semantic-capability"
      operation.actionIdentity shouldBe "AssessPartitionSemanticInputs"
      operation.operation shouldBe StateMachineOperationIdentity("SplitSemanticService", "assessPartitionSemanticInputs")
      operation.inputType.map(_.value) shouldBe Some(J.requestCodec.typeIdentity)
      operation.resultType.map(_.value) shouldBe Some(J.resultCodec.typeIdentity)
      completion.size shouldBe 1
      completion.head.service shouldBe "SplitPhaseService"
      completion.head.inputType shouldBe Some(J.resultCodec.typeIdentity)
      completion.head.resultType shouldBe Some("SplitPhaseSemanticSubmissionResult")
      val spi = candidate.workflow.requiredSpi.find(_.capability == operation.identity.capability).get
      val action = candidate.workflow.actions.find(_.identity == operation.actionIdentity).get
      spi.operation shouldBe action.operation
      candidate.workflow.requiredSpi.map(_.capability) shouldBe Vector("assess-partition-semantic-capability",
        "assess-handoff-scope-capability", "split-human-direction-capability", "assess-reasoning-demand-capability", "reasoning-human-direction-capability")
    }
    "generate purpose-typed payload shapes matching the converted application JSON without a schema adapter" in {
      Given("an actual Workflow-computed request and raw semantic result")
      val context = prepared
      val result = S.Judgment(S.Decision.Enriched, "bounded-analysis",
        Vector(S.NovelEstimate(context.request.items.head.identity, 160, 180, "bounded-work", "new-closure")),
        Vector(S.BoundaryJudgment(context.request.boundaries.head.edge.afterItem, EdgeDisposition.Either, "independent-closure")),
        Vector(S.EvidenceReference("source-analysis", "1")))
      val draft = context.request
      val request = S.Request(draft.context, draft.source, draft.items, draft.boundaries,
        S.Reply(S.ResultReference("issued-result", "1"), S.ResolutionReferences(
          Some(E.EstimateReference("issued-estimates", "1")),
          Some(SplitPhaseExplicitBoundaryValidation.HandoffPlanReference("issued-handoffs", "1")))))
      val requestJson = take(J.requestCodec.encodeC(request)).hcursor.downField("payload").focus.get
      val resultJson = take(J.resultCodec.encodeC(result)).hcursor.downField("payload").focus.get
      When("the unchanged application payloads are decoded as the actual CML-generated raw value classes")
      val generatedRequest = requestJson.as[GeneratedRequest].fold(e => fail(e.toString), identity)
      val generatedResult = resultJson.as[GeneratedResult].fold(e => fail(e.toString), identity)
      Then("purpose references and full nested closure survive while result authority and execution selectors are absent")
      generatedRequest.asJson shouldBe requestJson
      generatedResult.asJson shouldBe resultJson
      generatedRequest.productElementNames.toSet shouldBe Set("context", "source", "items", "boundaries", "reply")
      generatedResult.productElementNames.toSet shouldBe Set("decision", "rationale", "estimates", "boundaries", "evidence")
      generatedRequest.reply.result.identity shouldBe request.reply.result.identity
      generatedRequest.reply.resolutions.estimates.get.identity shouldBe request.reply.resolutions.estimates.get.identity
      generatedRequest.context.identity shouldBe context.reference.identity
      generatedRequest.source.revision shouldBe context.request.source.revision
      generatedRequest.items.head.closure.authority.identity shouldBe context.request.items.head.closure.authority.identity
      generatedResult.boundaries.head.productElementNames.toSet shouldBe Set("afterItem", "disposition", "rationale")
      generatedResult.decision shouldBe S.Decision.Enriched.toString
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionSemanticResult(\"Enriched\", \"rationale\", Vector.empty, Vector.empty, Vector.empty, nextOperation = \"apply\")")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionBoundaryJudgment(null, \"Either\", \"rationale\", authority = null)")
    }
    "retain the converted Judgment policy and every Required correlation in the actual component JAR" in {
      Given("the normal packageBin artifact containing the complete official ABI resources")
      val jar = Path.of("target/scala-3.3.8/textus-sm-workflow_3-0.1.0-SNAPSHOT.jar")
      Files.isRegularFile(jar) shouldBe true
      val loader = new URLClassLoader(Array(jar.toUri.toURL), null)
      try {
        When("the component catalog is read without filtering or rewriting its resources")
        val catalog = take(SplitPhaseRuntimeCatalog.loadC(loader))
        val model = catalog.producer.models.find(_.model.workflow.exists(_.identity.value == "SplitPhaseWorkflow")).get
        val judgment = model.judgments.find(_.identity.value == "AssessPartitionSemanticInputs").get
        Then("the packaged execution definition resolves the actual partition capability and its requested data")
        catalog.workflow.workflow.identity shouldBe "SplitPhaseWorkflow"
        catalog.definition.workflowRevision.value shouldBe "split-phase-business-operation-v14"
        judgment.alternatives.map(_.value) shouldBe Vector("Enriched", "DecisionNeeded")
        judgment.criteria.map(_.value) shouldBe Vector("exact-ordered-gap-coverage", "known-work-preserved",
          "workflow-owned-existing-authority", "no-participant-execution-selectors")
        judgment.evidenceProvenance.value shouldBe "host-owned-result-reference"
        judgment.operation.inputType.map(_.value) shouldBe Some(J.requestCodec.typeIdentity)
        judgment.operation.resultType.map(_.value) shouldBe Some(J.resultCodec.typeIdentity)
        model.requiredSpi.map(_.identity.value).toSet shouldBe Set("assess-partition-semantic-capability",
          "assess-handoff-scope-capability", "split-human-direction-capability", "assess-reasoning-demand-capability", "reasoning-human-direction-capability")
      } finally loader.close()
    }
  }
}
