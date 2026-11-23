/**
 * @since   Nov. 21, 2026
 * @version Nov. 23, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.spi.{ResolvedSpiMember, SpiMemberMetadata}
import org.goldenport.cncf.spi.ai.runner.{AiChatRequest, AiChatResponse, AiGenerateRequest, AiGenerateResponse, AiRecordRequest, AiRecordResponse, AiRunner, AiRunnerSocketSet}
import org.goldenport.protocol.Property
import org.goldenport.cncf.resource.{InMemoryUrlResourceProvider, ResourceAccessTestProfile, ResourceReference, ResourceUrlPolicy}
import java.nio.file.Path
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseApiReviewSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The supplied API review byte bound" should {
    "retain the API review operation in normal deferred declarations without publishing it to MCP" in {
      val name = "runGoalPhaseApiReview"
      val generated = SmWorkflowComponent.GoalPhaseServiceService.operations.operations.toVector.find(x => java.util.Objects.equals(x.name, name)).getOrElse(fail("Expected generated operation"))
      val deferred = ThreeWorkflowLauncherAssembly.deferredDeclarations.find(x => "GoalPhaseService".equals(x.name)).getOrElse(fail("Expected deferred service"))
        .operations.operations.toVector.filter(x => java.util.Objects.equals(x.name, name))
      deferred.size shouldBe 1
      val deferredOperation = deferred.headOption.getOrElse(fail("Expected deferred operation"))
      deferredOperation.specification.request shouldBe generated.specification.request
      deferredOperation.specification.response shouldBe generated.specification.response
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should not contain ("GoalPhaseService." + name)
    }
    "request and enforce the exact review outcome vocabulary" in {
      val schema = io.circe.parser.parse(GoalPhaseApiReview.schema.toJsonStringC.toOption.getOrElse(fail("Expected successful result"))).toOption.getOrElse(fail("Expected successful result"))
      schema.hcursor.downField("properties").downField("outcome").downField("enum").focus.flatMap(_.asArray).flatMap { values =>
        val strings = values.flatMap(_.asString)
        java.lang.Integer.compare(strings.size, values.size) match {
          case 0 => Some(strings)
          case _ => None
        }
      } shouldBe
        Some(Vector("accept", "repair", "decision-needed"))
      val deferral = schema.hcursor.downField("properties").downField("issueReport").downField("properties")
        .downField("deferredIssues").downField("items")
      deferral.downField("required").as[Vector[String]].toOption shouldBe Some(Vector("issueIdentity", "conditionIdentities"))
      GoalPhaseApiReview.reviewResultInstructions should include ("issueReport.deferredIssues")
      GoalPhaseApiReview.reviewResultInstructions should include ("do not use \"accepted\"")
      GoalPhaseApiReview.reviewResultInstructions should include ("Write newly authored review summaries, findings and rationales in English.")
      GoalPhaseApiReview.reviewResultInstructions should include ("Preserve quoted approvals and log excerpts verbatim in their original language; preserve typed values and evidence references exactly.")
      val facts = Map("ai.execution.provider" -> "ollama", "ai.usage.tokens" -> "42", "secret" -> "hidden")
      for (outcome <- Vector("accept", "repair", "decision-needed", "accepted", "Accept", "accept ")) {
        val record = Record.dataAuto("outcome" -> outcome, "summary" -> "review", "findings" -> Vector.empty[String])
        val result = GoalPhaseApiReview.providerResult("attempt", "continuation", record, Some("gpt-oss:20b"), facts)
        result.status shouldBe (if (Set("accept", "repair", "decision-needed")(outcome)) "COMPLETED" else "FAILED")
        val valid = Set("accept", "repair", "decision-needed")(outcome)
        result.result.map(_.outcome) shouldBe Option.when(valid)(outcome)
        result.providerRecord shouldBe Option.when(!valid)(record)
        if (!valid) {
          val wire = io.circe.parser.parse(result.toRecord().toJsonStringC.toOption.getOrElse(fail("Expected successful result"))).toOption.getOrElse(fail("Expected successful result"))
          val original = io.circe.parser.parse(record.toJsonStringC.toOption.getOrElse(fail("Expected successful result"))).toOption.getOrElse(fail("Expected successful result"))
          wire.hcursor.downField("providerRecord").focus shouldBe Some(original)
        }
        result.model shouldBe Some("gpt-oss:20b")
        result.provider shouldBe Some("ollama")
        val metadata = result.metadata.getOrElse(fail("Expected metadata")).toJsonStringC.toOption.getOrElse(fail("Expected successful result"))
        metadata should include ("ai.usage.tokens")
        metadata should not include ("secret")
      }
      val report = V.GoalPhaseIssueReport(
        Vector(V.GoalPhaseIssueDisposition("issue-1", "NOT_REVIEWED", "Wholly within the adopted deferral", None)),
        Vector(V.GoalPhaseIssueDeferral("issue-1", Vector("usage-contract"))))
      val response = Record.dataAuto("outcome" -> "accept", "summary" -> "review", "findings" -> Vector.empty[String],
        "issueReport" -> report.toRecord())
      val result = GoalPhaseApiReview.providerResult("attempt", "continuation", response, Some("gpt-oss:20b"), facts)
      result.status shouldBe "COMPLETED"
      result.result.flatMap(_.issueReport) shouldBe Some(report)
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
      result.metadata.getOrElse(fail("Expected metadata")).toJsonStringC.toOption.getOrElse(fail("Expected successful result")) should not include ("secret")
      val wire = io.circe.parser.parse(result.toRecord().toJsonStringC.toOption.getOrElse(fail("Expected successful result"))).toOption.getOrElse(fail("Expected successful result"))
      val original = io.circe.parser.parse(record.toJsonStringC.toOption.getOrElse(fail("Expected successful result"))).toOption.getOrElse(fail("Expected successful result"))
      wire.hcursor.downField("providerRecord").focus shouldBe Some(original)
    }
    "retain a qualified reservation basis separately from measured usage and charges" in {
      val key = "ai.usage.reservation_basis"
      val facts = Map("ai.usage.total_tokens" -> "9", "ai.usage.allowance_admission" -> "verified-included")
      val record = Record.dataAuto("outcome" -> "accept", "summary" -> "review", "findings" -> Vector.empty[String])
      val response = AiRecordResponse(record, Some("pinned"), facts)
      val review = GoalPhaseApiReview.providerResult("attempt", "continuation", record, response.model, facts)
      review.metadata.flatMap(_.getString("ai.usage.allowance_admission")) shouldBe Some("verified-included")
      review.metadata.flatMap(_.getAny("ai.usage.cost_microunits")) shouldBe None
      val compiler = GoalPhaseApiReview.boundedRecordEvidence(response).getOrElse(fail("Expected compiler evidence"))
      compiler.hcursor.get[String]("ai.usage.allowance_admission").toOption shouldBe Some("verified-included")
      compiler.hcursor.downField("ai.usage.cost_microunits").focus shouldBe None
      val unrecognized = response.copy(metadata = facts.updated("ai.usage.allowance_admission", "unverified"))
      GoalPhaseApiReview.boundedRecordEvidence(unrecognized).flatMap(
        _.hcursor.downField("ai.usage.allowance_admission").focus) shouldBe None

      val uncertain = Consequence.operationIllegal("ai.task.uncertain", "fixture").conclusion
      for (basis <- Vector("verified-daily-reservation", "verified-promotional-credit-reservation", "unverified")) {
        val metadata = facts.updated(key, basis)
        val expected = Option.when(basis != "unverified")(basis)
        val reviewWithBasis = GoalPhaseApiReview.providerResult("attempt", "continuation", record, response.model, metadata)
        reviewWithBasis.metadata.flatMap(_.getString(key)) shouldBe expected
        val compilerWithBasis = GoalPhaseApiReview.boundedRecordEvidence(response.copy(metadata = metadata))
          .getOrElse(fail("Expected compiler evidence"))
        compilerWithBasis.hcursor.get[String](key).toOption shouldBe expected
        compilerWithBasis.hcursor.get[String]("ai.usage.total_tokens").toOption shouldBe Some("9")
        compilerWithBasis.hcursor.downField("ai.usage.cost_microunits").focus shouldBe None
        val failed = uncertain.copy(observation = uncertain.observation.copy(properties = metadata))
        GoalPhaseApiReview.failureResult("attempt", "continuation", failed).metadata.flatMap(_.getString(key)) shouldBe expected
        GoalPhaseApiReview.boundedFailureEvidence(failed).flatMap(_.hcursor.get[String](key).toOption) shouldBe expected
      }
    }
    "retain only observed safe provider identity in failure and compiler evidence" in {
      val base = Consequence.operationIllegal("ai.task.uncertain", "fixture").conclusion
      def observed(properties: Map[String, String]) =
        base.copy(observation = base.observation.copy(properties = properties))
      val facts = Map("ai.execution.model" -> "actual-model", "ai.execution.provider" -> "openai",
        "ai.usage.untrusted" -> "secret", "untrusted-extra" -> "secret")
      val review = GoalPhaseApiReview.failureResult("attempt", "continuation", observed(facts))
      review.model shouldBe Some("actual-model")
      review.provider shouldBe Some("openai")
      val evidence = GoalPhaseApiReview.boundedFailureEvidence(observed(facts)).getOrElse(fail("Expected compiler evidence"))
      evidence.hcursor.get[String]("ai.execution.model").toOption shouldBe review.model
      evidence.hcursor.get[String]("ai.execution.provider").toOption shouldBe review.provider
      evidence.hcursor.downField("ai.usage.untrusted").focus shouldBe None
      evidence.hcursor.downField("untrusted-extra").focus shouldBe None
      for (bad <- Vector("", "bad\nvalue", "x" * 257)) {
        val invalid = observed(Map("ai.execution.model" -> bad, "ai.execution.provider" -> bad))
        val result = GoalPhaseApiReview.failureResult("attempt", "continuation", invalid)
        result.model shouldBe None
        result.provider shouldBe None
        GoalPhaseApiReview.boundedFailureEvidence(invalid) shouldBe None
      }
      val absent = GoalPhaseApiReview.failureResult("attempt", "continuation", observed(Map.empty))
      absent.model shouldBe None
      absent.provider shouldBe None
    }
    "retain only closed failure diagnostics without inventing provider facts" in {
      def wire(c: org.goldenport.Conclusion): String = {
        val result = GoalPhaseApiReview.failureResult("attempt", "continuation", c)
        result.status shouldBe "UNCERTAIN"
        result.message shouldBe "Provider call did not return a usable result"
        result.provider shouldBe None
        result.toRecord().toJsonStringC.toOption.getOrElse(fail("Expected successful result"))
      }
      val http = wire(Consequence.serviceUnavailable("AI provider request failed: category=rate_limited status=429").conclusion)
      http should include ("rate_limited")
      http should include ("429")
      http should include ("taxonomyCategory")
      val timeout = wire(Consequence.serviceUnavailable("private token sk-secret", "https://secret.example", new java.net.SocketTimeoutException("secret detail")).conclusion)
      timeout should include ("timeout")
      timeout should not include ("sk-secret")
      timeout should not include ("secret.example")
      timeout should not include ("providerStatus")
      val opaque = wire(Consequence.serviceUnavailable("AI provider request failed: category=timeout status=504 token=sk-secret").conclusion)
      opaque should not include ("providerStatus")
      opaque should not include ("providerCategory")
      opaque should not include ("causeKind")
      opaque should not include ("sk-secret")
      val badStatus = wire(Consequence.serviceUnavailable("AI provider request failed: category=timeout status=999").conclusion)
      badStatus should not include ("providerStatus")
    }
    "omit absent and invalid HTTP evidence while preserving empty body text" in {
      val record = Record.dataAuto("outcome" -> "accept", "summary" -> "review", "findings" -> Vector.empty[String])
      def metadata(values: Map[String, String]): Option[io.circe.Json] =
        GoalPhaseApiReview.providerResult("attempt", "continuation", record, None, values).metadata.map { value =>
          io.circe.parser.parse(value.toJsonStringC.toOption.getOrElse(fail("Expected metadata"))).toOption.getOrElse(fail("Expected JSON"))
        }
      metadata(Map.empty) shouldBe None
      val invalid = Map("ai.provider.http.status" -> "600", "ai.provider.http.request-id" -> "req_bad!",
        "ai.provider.http.body" -> ("あ" * 349526), "ai.provider.http.body-omitted" -> "other",
        "ai.provider.http.header" -> "secret")
      metadata(invalid) shouldBe None
      val empty = metadata(Map("ai.provider.http.body" -> "")).getOrElse(fail("Expected empty body"))
      empty.hcursor.downField("providerHttp").downField("body").as[String].toOption shouldBe Some("")
      val base = Consequence.serviceUnavailable("fixture").conclusion
      val conclusion = base.copy(observation = base.observation.copy(properties = invalid))
      val failed = GoalPhaseApiReview.failureResult("attempt", "continuation", conclusion)
      val json = io.circe.parser.parse(failed.metadata.getOrElse(fail("Expected diagnostics")).toJsonStringC.toOption.getOrElse(fail("Expected JSON")))
        .toOption.getOrElse(fail("Expected JSON"))
      json.hcursor.downField("providerHttp").focus shouldBe None
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
    "bind bounded reviews to an advertised producer and the exact issued task" in {
      Given("one legacy runner and one producer advertising the bounded-task capability")
      val execution = ExecutionContext.create()
      val input = V.GoalPhaseApiReviewInput("session", "work:exact", "attempt", "file:///package", Some(true))
      var calls = 0
      var captured: Option[AiRecordRequest] = None
      var metadata = Map.empty[String, String]
      var model: Option[String] = Some("actual-model")
      var terminal: Option[String] = None
      var report = Option.empty[Record]
      val runner = new AiRunner {
        def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] = Consequence.operationInvalid("unexpected")
        def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] = Consequence.operationInvalid("unexpected")
        def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] = {
          calls += 1
          captured = Some(req)
          terminal match {
            case Some(operation) =>
              val failed = Consequence.operationIllegal(operation, "provider outcome").conclusion
              Consequence.Failure(failed.copy(observation = failed.observation.copy(properties =
                failed.observation.properties ++ metadata)))
            case None =>
              val result = Record.dataAuto("outcome" -> "accept", "summary" -> "review", "findings" -> Vector.empty[String])
              val returned = report.fold(result)(value => result.appendField("acceptance", value))
              Consequence.success(AiRecordResponse(returned, model, metadata))
          }
        }
      }
      val providerid = ComponentId("org.example.BoundedReviewFixture")
      val legacy = new AiRunnerSocketSet {}
      legacy.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
        SpiMemberMetadata("ai-runner", "legacy", ComponentInstanceId.default(providerid), isDefault = true))))
      val bounded = new AiRunnerSocketSet {}
      bounded.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
        SpiMemberMetadata("ai-runner", "bounded", ComponentInstanceId.default(providerid), capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
      def run(socket: AiRunnerSocketSet, packet: String = "package"): V.GoalPhaseApiReviewResult =
        GoalPhaseApiReview.reviewWithSocketC(Some(socket), true, input, "continuation", packet)(using execution)
          .toOption.getOrElse(fail("Expected transport result"))
      When("only the legacy runner is available")
      Then("selection is unavailable without invoking that runner")
      run(legacy).status shouldBe "UNAVAILABLE"
      calls shouldBe 0
      When("the advertised producer echoes the task binding and reports its actual provider and model")
      metadata = Map("ai.task.contract" -> "textus-ai.bounded-task.v1", "ai.task.id" -> "attempt",
        "ai.task.continuation" -> "continuation", "ai.task.input-reference" -> "work:exact", "ai.execution.provider" -> "openai")
      run(bounded).status shouldBe "COMPLETED"
      val request = captured.getOrElse(fail("Expected request"))
      request.metadata shouldBe (metadata - "ai.execution.provider" + ("ai.task.kind" -> "independent-review"))
      request.requirement.provider shouldBe Some("openai")
      request.requirement.tools shouldBe empty
      request.properties should contain (Property("ai.record.retry-limit", "0", None))
      When("any task binding is missing or mismatched, or actual execution identity is absent")
      val matching = metadata
      for (key <- Vector("ai.task.contract", "ai.task.id", "ai.task.continuation", "ai.task.input-reference", "ai.execution.provider")) {
        metadata = matching - key
        val missing = run(bounded)
        missing.status shouldBe "UNCERTAIN"
        missing.result shouldBe None
        missing.providerRecord shouldBe None
        metadata = matching.updated(key, if (key == "ai.execution.provider") "" else "crossed")
        run(bounded).status shouldBe "UNCERTAIN"
      }
      metadata = matching
      model = None
      val unqualified = run(bounded)
      Then("unqualified results cannot complete or expose the provider record")
      unqualified.status shouldBe "UNCERTAIN"
      unqualified.result shouldBe None
      unqualified.providerRecord shouldBe None

      When("the producer reports a final failure or an uncertain dispatch")
      metadata = Map("ai.execution.provider" -> "openai", "ai.execution.model" -> "observed-model",
        "ai.provider.http.request-id" -> "req_terminal_fixture", "untrusted-extra" -> "secret",
        "ai.usage.total_tokens" -> "7", "ai.usage.total_tokens_source" -> "observed")
      terminal = Some("ai.task.failed")
      val failed = run(bounded)
      failed.status shouldBe "FAILED"
      failed.provider shouldBe Some("openai")
      failed.model shouldBe Some("observed-model")
      failed.result shouldBe None
      val receipt = failed.metadata.flatMap(_.toJsonStringC.toOption).flatMap(io.circe.parser.parse(_).toOption)
        .getOrElse(fail("Expected retained provider failure facts"))
      receipt.hcursor.get[String]("ai.usage.total_tokens").toOption shouldBe Some("7")
      receipt.hcursor.get[String]("ai.execution.model").toOption shouldBe Some("observed-model")
      receipt.hcursor.downField("untrusted-extra").focus shouldBe None
      receipt.hcursor.downField("providerHttp").get[String]("requestId").toOption shouldBe Some("req_terminal_fixture")
      terminal = Some("ai.task.uncertain")
      val uncertain = run(bounded)
      uncertain.status shouldBe "UNCERTAIN"
      uncertain.provider shouldBe Some("openai")
      uncertain.model shouldBe Some("observed-model")

      When("the configured Plan contains only a Phase condition for this Step review")
      metadata = matching
      model = Some("actual-model")
      terminal = None
      val packet = """{"kind":"ReviewStep","contract":{"effectiveAcceptance":{"conditions":[
        {"identity":"phase-ready","scope":"phase","required":true,"description":"The Phase is ready.","source":"adopted"}
      ]}}}"""
      val missingReport = run(bounded, packet)
      missingReport.status shouldBe "FAILED"
      missingReport.result shouldBe None
      val submitted = captured.getOrElse(fail("Expected acceptance-bound request"))
      val required = submitted.schema.toJsonStringC.toOption.flatMap(io.circe.parser.parse(_).toOption)
        .flatMap(_.hcursor.get[Vector[String]]("required").toOption).getOrElse(fail("Expected schema requirements"))
      required should contain ("acceptance")
      submitted.prompt should include ("even when no conditions apply")
      submitted.prompt should include ("acceptance.conditions must be an empty array")
      report = Some(Record.dataAuto("conditions" -> Vector.empty[Record], "advisories" -> Vector.empty[String]))
      val completed = run(bounded, packet)
      completed.status shouldBe "COMPLETED"
      completed.result.flatMap(_.acceptance).map(_.conditions.size) shouldBe Some(0)
      run(bounded, packet.replace("ReviewStep", "ReviewPhase"))
      captured.getOrElse(fail("Expected Phase review request")).prompt should include (
        "Only these condition identities may appear in acceptance.conditions for this issued review: [\"phase-ready\"]")
    }
    "decline an oversized UTF-8 package before looking up review material" in {
      GoalPhaseApiReview.packageC("あ" * 200000, null, "sm-workflow-work:fixture", 512 * 1024).toOption shouldBe Some(false)
    }
    "decline a bounded acquisition refusal without invoking the provider and retain other failures" in {
      val url = "file:///private/tmp/review-package.json"
      val reference = ResourceReference.parseC(url).toOption.getOrElse(fail("Expected successful result"))
      val text = "{\"content\":\"日本\"}"
      val context = ExecutionContext.withResourceAccessTestProfile(ExecutionContext.create(), ResourceAccessTestProfile(
        urlPolicy = ResourceUrlPolicy(fileRoots = Vector(Path.of("/private/tmp"))),
        urlProviders = Vector(new InMemoryUrlResourceProvider("file", Map(url -> text)))))
      val input = V.GoalPhaseApiReviewInput("session", "work", "attempt", url, None)
      var invoked = false
      def run(s: String): Consequence[V.GoalPhaseApiReviewResult] = {
        invoked = true
        Consequence.stateConflict("provider must not run")
      }
      val size = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
      val declined = GoalPhaseApiReview.acquireAndReviewC(context, reference, size - 1, null, input, "continuation", run)
      declined.toOption.map(_.status) shouldBe Some("DECLINED")
      invoked shouldBe false
      val missing = ResourceReference.parseC("file:///private/tmp/missing.json").toOption.getOrElse(fail("Expected successful result"))
      GoalPhaseApiReview.acquireAndReviewC(context, missing, size, null, input, "continuation", run).isFaillure shouldBe true
      invoked shouldBe false
    }
    "derive compact check facts and keep issue evidence references" in {
      val check = io.circe.parser.parse("""[{"identity":"test","termination":"exited","exitCode":0,"sbt":{"outcome":"succeeded"},"stdout":"large"}]""").toOption.getOrElse(fail("Expected successful result"))
      val facts = GoalPhaseApiReview.checkFacts(check).getOrElse(fail("Expected check facts"))
      facts.hcursor.downN(0).downField("exitCode").focus.flatMap(_.asNumber).flatMap(_.toInt) shouldBe Some(0)
      facts.noSpaces should not include "stdout"
      GoalPhaseApiReview.reviewResultInstructions should include ("RECURRING requires a unique 1-based findingOrdinal")
      val issue = io.circe.Json.obj("issues" -> io.circe.Json.arr(io.circe.Json.obj(
        "summary" -> io.circe.Json.fromString("x" * 2000), "evidenceReference" -> io.circe.Json.fromString("actual:receipt"))))
      val projected = GoalPhaseApiReview.compactIssues(issue).getOrElse(fail("Expected compact issues"))
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
      val boundPacket = packet.replace("PLAN_PLACEHOLDER", plan.toRecord().toJsonStringC.toOption.getOrElse(fail("Expected successful result")))
      val originalText = "承認します。 ログ: 接続成功"
      val reviewPacket = boundPacket.replace("actual requirements", originalText)
      val url = "file:///private/tmp/spi-review-package.json"
      val invalidUrl = "file:///private/tmp/spi-review-invalid.json"
      val execution = ExecutionContext.withResourceAccessTestProfile(ExecutionContext.create(), ResourceAccessTestProfile(
        urlPolicy = ResourceUrlPolicy(fileRoots = Vector(Path.of("/private/tmp"))),
        urlProviders = Vector(new InMemoryUrlResourceProvider("file", Map(
          url -> reviewPacket, invalidUrl -> reviewPacket.replace("checks:1", "checks:other"))))))
      var requests = Vector.empty[AiRecordRequest]
      val httpEvidence = Map("ai.provider.http.request-id" -> "req_abc-12",
        "ai.provider.http.authorization" -> "secret-header")
      val successBody = """{"choices":[{"message":{"content":"{\"outcome\":\"decision-needed\",\"summary\":\"Review needs a decision\",\"findings\":[]}"}}]}"""
      val failureBody = """{"error":{"message":"Rate limit exceeded; credential [REDACTED]"}}"""
      Given("the runner returns HTTP 200 evidence on success and redacted HTTP 429 evidence on failure")
      var failCall = false
      val runner = new AiRunner {
        def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
          Consequence.operationInvalid("unexpected generate")
        def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] =
          Consequence.operationInvalid("unexpected chat")
        def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] = {
          requests :+= req
          if (failCall) {
            val c = Consequence.serviceUnavailable("AI provider request failed: category=rate_limited status=429").conclusion
            val evidence = httpEvidence ++ Map("ai.provider.http.status" -> "429", "ai.provider.http.body" -> failureBody,
              "secret" -> "hidden")
            Consequence.Failure(c.copy(observation = c.observation.copy(properties = evidence)))
          } else Consequence.success(AiRecordResponse(
            Record.dataAuto("outcome" -> "decision-needed", "summary" -> "Review needs a decision", "findings" -> Vector.empty[String]),
            Some("fixture-model"), httpEvidence ++ Map("ai.execution.provider" -> "fixture-provider", "ai.usage.tokens" -> "42",
              "ai.provider.http.status" -> "200", "ai.provider.http.body" -> successBody)))
        }
      }
      val socket = new AiRunnerSocketSet {}
      val providerId = ComponentId("org.example.ReviewFixture")
      socket.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
        SpiMemberMetadata("ai-runner", "review-fixture", ComponentInstanceId.default(providerId), isDefault = true))))
      val component = new Component() {}.withPort(Component.Port.input(socket))
      val core = ActionCall.Core(null, execution, Some(component), None)
      val input = V.GoalPhaseApiReviewInput("session", resource, "attempt", url, None)
      def run(text: String): Consequence[V.GoalPhaseApiReviewResult] =
        GoalPhaseApiReview.runC(core, input, "continuation", text)
      When("the supplied review completes through the runner SPI")
      val result = GoalPhaseApiReview.acquireAndReviewC(execution, ResourceReference.parseC(url).toOption.getOrElse(fail("Expected successful result")),
        512 * 1024, actual, input, "continuation", run).toOption.getOrElse(fail("Expected successful result"))
      Then("the serialized result retains the available HTTP response evidence")
      result.status shouldBe "COMPLETED"
      result.result.map(_.outcome) shouldBe Some("decision-needed")
      result.model shouldBe Some("fixture-model")
      result.provider shouldBe Some("fixture-provider")
      result.metadata.getOrElse(fail("Expected metadata")).toJsonStringC.toOption.getOrElse(fail("Expected successful result")) should include ("42")
      requests.size shouldBe 1
      val recorded = requests.headOption.getOrElse(fail("Expected recorded request"))
      recorded.prompt should endWith (reviewPacket)
      recorded.prompt should include (originalText)
      recorded.requirement.tools shouldBe empty
      recorded.properties should contain (Property("ai.record.retry-limit", "0", None))
      recorded.properties should contain (Property("ai.record.capture-provider-http", "true", None))
      def serializedHttp(value: V.GoalPhaseApiReviewResult): io.circe.Json = {
        val wire = io.circe.parser.parse(value.toRecord().toJsonStringC.toOption.getOrElse(fail("Expected serialization")))
          .toOption.getOrElse(fail("Expected JSON"))
        wire.noSpaces should not include "secret-header"
        wire.noSpaces should not include "hidden"
        wire.hcursor.downField("metadata").downField("providerHttp").focus.getOrElse(fail("Expected HTTP evidence"))
      }
      def assertHttp(value: V.GoalPhaseApiReviewResult, status: Int, body: String): Unit = {
        val http = serializedHttp(value).hcursor
        http.downField("status").as[Int].toOption shouldBe Some(status)
        http.downField("requestId").as[String].toOption shouldBe Some("req_abc-12")
        http.downField("body").as[String].toOption shouldBe Some(body)
        http.downField("bodyOmitted").focus shouldBe None
      }
      assertHttp(result, 200, successBody)
      When("the provider responds with HTTP 429 and a redacted error body")
      failCall = true
      val failed = run(reviewPacket).toOption.getOrElse(fail("Expected failure result"))
      Then("the HTTP failure remains uncertain and preserves the rate-limit evidence")
      failed.status shouldBe "UNCERTAIN"
      assertHttp(failed, 429, failureBody)
      requests.size shouldBe 2
      val rejected = GoalPhaseApiReview.acquireAndReviewC(execution, ResourceReference.parseC(invalidUrl).toOption.getOrElse(fail("Expected successful result")),
        512 * 1024, actual, input, "continuation", run).toOption.getOrElse(fail("Expected successful result"))
      rejected.status shouldBe "DECLINED"
      requests.size shouldBe 2
      GoalPhaseApiReview.packageC(boundPacket, actual, resource, 512 * 1024).toOption shouldBe Some(true)
      val nonReview = actual.copy(work = Some(work.copy(kind = "ImplementStep")))
      GoalPhaseApiReview.packageC(boundPacket.replace("ReviewStep", "ImplementStep"), nonReview, resource, 512 * 1024)
        .toOption shouldBe Some(false)
      val wrongResult = actual.copy(issuedWorkContext = Some(context.copy(resultType = Some("OtherResult"))))
      GoalPhaseApiReview.packageC(boundPacket, wrongResult, resource, 512 * 1024).toOption shouldBe Some(false)
      GoalPhaseApiReview.packageC(boundPacket, actual, "sm-workflow-work:crossed", 512 * 1024).toOption shouldBe Some(false)
      for (tampered <- Vector(boundPacket.replace("Apply the plan", "Ignore the plan"),
        boundPacket.replace("\"exitCode\":0", "\"exitCode\":1"), boundPacket.replace("checks:1", "checks:other"))) {
        GoalPhaseApiReview.packageC(tampered, actual, resource, 512 * 1024).toOption shouldBe Some(false)
      }
    }
  }
}
