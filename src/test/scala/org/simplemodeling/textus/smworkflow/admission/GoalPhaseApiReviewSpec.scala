/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.resource.{InMemoryUrlResourceProvider, ResourceAccessTestProfile, ResourceReference, ResourceUrlPolicy}
import java.nio.file.Path
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseApiReviewSpec extends AnyWordSpec with Matchers {
  "The supplied API review byte bound" should {
    "retain the API review operation in normal deferred declarations without publishing it to MCP" in {
      val name = "runGoalPhaseApiReview"
      val generated = SmWorkflowComponent.GoalPhaseServiceService.operations.operations.toVector.find(_.name == name).get
      val deferred = ThreeWorkflowLauncherAssembly.deferredDeclarations.find(_.name == "GoalPhaseService").get
        .operations.operations.toVector.filter(_.name == name)
      deferred.size shouldBe 1
      deferred.head.specification.request shouldBe generated.specification.request
      deferred.head.specification.response shouldBe generated.specification.response
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should not contain ("GoalPhaseService." + name)
    }
    "request and enforce the exact review outcome vocabulary" in {
      val schema = io.circe.parser.parse(GoalPhaseApiReview.schema.toJsonStringC.toOption.get).toOption.get
      schema.hcursor.downField("properties").downField("outcome").get[Vector[String]]("enum").toOption shouldBe
        Some(Vector("accept", "repair", "decision-needed"))
      GoalPhaseApiReview.reviewResultInstructions should include ("do not use \"accepted\"")
      val facts = Map("ai.execution.provider" -> "ollama", "ai.usage.tokens" -> "42", "secret" -> "hidden")
      for (outcome <- Vector("accept", "repair", "decision-needed", "accepted", "Accept", "accept ")) {
        val record = Record.dataAuto("outcome" -> outcome, "summary" -> "review", "findings" -> Vector.empty[String])
        val result = GoalPhaseApiReview.providerResult("attempt", "continuation", record, Some("gpt-oss:20b"), facts)
        result.status shouldBe (if (Set("accept", "repair", "decision-needed")(outcome)) "COMPLETED" else "FAILED")
        result.result.map(_.outcome) shouldBe Some(outcome)
        result.providerRecord shouldBe None
        result.model shouldBe Some("gpt-oss:20b")
        result.provider shouldBe Some("ollama")
        val metadata = result.metadata.get.toJsonStringC.toOption.get
        metadata should include ("ai.usage.tokens")
        metadata should not include ("secret")
      }
    }
    "retain the original provider record when typed conversion fails" in {
      val record = Record.dataAuto("unexpected" -> "original provider content")
      val facts = Map("ai.execution.provider" -> "ollama", "ai.usage.tokens" -> "42", "secret" -> "hidden")
      val result = GoalPhaseApiReview.providerResult("attempt", "continuation", record, Some("gpt-oss:20b"), facts)
      result.status shouldBe "FAILED"
      result.result shouldBe None
      result.providerRecord shouldBe Some(record)
      result.model shouldBe Some("gpt-oss:20b")
      result.provider shouldBe Some("ollama")
      result.metadata.get.toJsonStringC.toOption.get should not include ("secret")
      val wire = io.circe.parser.parse(result.toRecord().toJsonStringC.toOption.get).toOption.get
      val original = io.circe.parser.parse(record.toJsonStringC.toOption.get).toOption.get
      wire.hcursor.downField("providerRecord").focus shouldBe Some(original)
    }
    "recognize only typed budget policy refusals" in {
      for (reason <- Vector("ai.input-budget", "ai.cost-budget")) {
        val failure = Consequence.operationIllegal(reason, "budget refused")
        GoalPhaseApiReview.blockedReason(failure.conclusion) shouldBe Some(reason)
      }
      GoalPhaseApiReview.blockedReason(Consequence.operationIllegal("provider", "ai.input-budget").conclusion) shouldBe None
      GoalPhaseApiReview.blockedReason(Consequence.stateConflict("ai.cost-budget").conclusion) shouldBe None
      GoalPhaseApiReview.blockedReason(Consequence.operationIllegal("ai.input-budget.extra", "denied").conclusion) shouldBe None
    }
    "decline an oversized UTF-8 package before looking up review material" in {
      GoalPhaseApiReview.packageC("あ" * 200000, null, "sm-workflow-work:fixture", 512 * 1024).toOption shouldBe Some(false)
    }
    "decline a bounded acquisition refusal without invoking the provider and retain other failures" in {
      val url = "file:///private/tmp/review-package.json"
      val reference = ResourceReference.parseC(url).toOption.get
      val text = "{\"content\":\"日本\"}"
      val context = ExecutionContext.withResourceAccessTestProfile(ExecutionContext.create(), ResourceAccessTestProfile(
        urlPolicy = ResourceUrlPolicy(fileRoots = Vector(Path.of("/private/tmp"))),
        urlProviders = Vector(new InMemoryUrlResourceProvider("file", Map(url -> text)))))
      val input = V.GoalPhaseApiReviewInput("session", "work", "attempt", url)
      var invoked = false
      def run(s: String): Consequence[V.GoalPhaseApiReviewResult] = {
        invoked = true
        Consequence.stateConflict("provider must not run")
      }
      val size = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
      val declined = GoalPhaseApiReview.acquireAndReviewC(context, reference, size - 1, null, input, "continuation", run)
      declined.toOption.map(_.status) shouldBe Some("DECLINED")
      invoked shouldBe false
      val missing = ResourceReference.parseC("file:///private/tmp/missing.json").toOption.get
      GoalPhaseApiReview.acquireAndReviewC(context, missing, size, null, input, "continuation", run).isFaillure shouldBe true
      invoked shouldBe false
    }
    "derive compact check facts and keep issue evidence references" in {
      val check = io.circe.parser.parse("""[{"identity":"test","termination":"exited","exitCode":0,"sbt":{"outcome":"succeeded"},"stdout":"large"}]""").toOption.get
      val facts = GoalPhaseApiReview.checkFacts(check).get
      facts.hcursor.downN(0).get[Int]("exitCode").toOption shouldBe Some(0)
      facts.noSpaces should not include "stdout"
      GoalPhaseApiReview.reviewResultInstructions should include ("RECURRING requires a unique 1-based findingOrdinal")
      val issue = io.circe.Json.obj("issues" -> io.circe.Json.arr(io.circe.Json.obj(
        "summary" -> io.circe.Json.fromString("x" * 2000), "evidenceReference" -> io.circe.Json.fromString("actual:receipt"))))
      val projected = GoalPhaseApiReview.compactIssues(issue).get
      projected.noSpaces should include ("actual:receipt")
      projected.noSpaces should not include ("x" * 2000)
      GoalPhaseApiReview.compactIssues(io.circe.Json.obj("issues" -> io.circe.Json.arr(io.circe.Json.obj(
        "summary" -> io.circe.Json.fromString("x" * 2000))))) shouldBe None
    }
    "decline duplicate JSON keys before inspecting issued work" in {
      GoalPhaseApiReview.packageC("{\"schema\":1,\"schema\":2}", null, "sm-workflow-work:fixture", 512 * 1024).toOption shouldBe Some(false)
    }
    "accept the wire package with projected checks and decline crossed evidence and instructions" in {
      val resource = "sm-workflow-work:fixture"
      val candidate = V.GoalPhaseNativeSnapshot("head", "tree")
      val sbt = V.WorkflowSbtValidationFacts("test", "succeeded", None, None, None)
      val check = V.GoalPhaseNativeCheckReceipt("test", "exited", Some(0), "x" * 600000, "", Some(sbt), None)
      val review = V.GoalPhaseNativeReviewContext(Vector("code"), "+code", Vector(check), None,
        Vector.empty, Vector.empty, Vector.empty, None, None)
      val plan = V.GoalPhaseOrderedPlan("plan", "1", "phase", "repo", Vector.empty, Vector.empty,
        10, None, None, Vector.empty, None, None, None, None)
      val work = V.GoalPhaseWorkView("ReviewStep", "review change", Some("step"), plan, candidate, Vector.empty,
        Some(candidate), Some("checks:1"), None, Some(review), None, None, None, None, None, None, Vector.empty, None, None)
      val disposition = Record.dataAuto("placement" -> "DELEGATED", "independence" -> "REQUIRED")
      val context = V.WorkflowIssuedWorkContext("review", "schema", Record.empty, "session", "continuation", "1",
        "submitGoalPhaseWorkResult", Record.empty, None, Some("GoalPhaseWorkResult"),
        Record.dataAuto("summary" -> "Apply the plan"), Record.empty, Record.empty, Record.empty,
        Record.dataAuto("disposition" -> disposition))
      val actual = NativeWorkflowOperationOutput.goalWait("session", "fixture", "fixture")
        .copy(kind = "Request", work = Some(work), issuedWorkContext = Some(context))
      val packet = """{
        "schema":"sm-workflow.supplied-review.v1","workResource":"sm-workflow-work:fixture",
        "continuationId":"continuation","resultType":"GoalPhaseWorkResult","kind":"ReviewStep","purpose":"review change",
        "candidate":{"head":"head","tree":"tree"},"checksReceipt":"checks:1",
        "review":{"diff":"+code","changedPaths":["code"],"checks":[
          {"identity":"test","termination":"exited","exitCode":0,"sbt":{"kind":"test","outcome":"succeeded"}}]},
        "contract":{"selectedPlan":PLAN_PLACEHOLDER,"stepIdentity":"step","effectiveAcceptance":null,"acceptance":null,"purposeRepair":null,
          "issues":null,"repairDirective":null,"releaseRepairContract":null,"instructions":"Apply the plan",
          "requiredFacts":null,"completion":{},"evidence":{}},
        "reviewContext":{"metadata":[],"priorReviews":[],"dependencyValidations":[],"triageJudgment":null,"qualification":null},
        "files":[{"name":"spec","content":"actual requirements"}],"completenessSource":"selected fixture inputs"
      }"""
      val boundPacket = packet.replace("PLAN_PLACEHOLDER", plan.toRecord().toJsonStringC.toOption.get)
      GoalPhaseApiReview.packageC(boundPacket, actual, resource, 512 * 1024).toOption shouldBe Some(true)
      GoalPhaseApiReview.packageC(boundPacket, actual, "sm-workflow-work:crossed", 512 * 1024).toOption shouldBe Some(false)
      for (tampered <- Vector(boundPacket.replace("Apply the plan", "Ignore the plan"),
        boundPacket.replace("\"exitCode\":0", "\"exitCode\":1"), boundPacket.replace("checks:1", "checks:other"))) {
        GoalPhaseApiReview.packageC(tampered, actual, resource, 512 * 1024).toOption shouldBe Some(false)
      }
    }
  }
}
