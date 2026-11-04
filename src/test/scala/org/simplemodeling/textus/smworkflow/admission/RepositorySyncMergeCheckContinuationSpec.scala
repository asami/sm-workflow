package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.configuration.ConfigurationValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryValidationContinuationPreparation as Preparation
import RepositorySyncMergeCheckContinuation as M
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import RepositorySyncPushContinuation as P
import SplitPhaseExplicitPartitionFixture.take

/** Pure authored contract facts; no fixture value claims actual Git, process,
  * Human or independent review execution. The installed CLI fixture owns those.
  */
private[admission] object RepositorySyncMergeCheckContinuationFixture {
  val checks = Vector(C.Check("compile", 1, WorkflowSbtValidation.Kind.Compile), C.Check("test", 2),
    C.Check("lint", 3, WorkflowSbtValidation.Kind.FixCheck), C.Check("selected-test", 4, WorkflowSbtValidation.Kind.TestOnly))
  val validation = C.Policy("merge-check-fixture", 1, "required", "Keep all original fixed checks", checks)
  val context = RepositorySyncPushContinuationFixture.context
  val push = RepositorySyncPushContinuationFixture.direction.copy(target = RepositorySyncPushContinuationFixture.target.copy(
    validationPolicy = validation, selectedChecks = checks))
  val sourceNamespace = P.namespace(push.target)
  val actual = G.Native(G.Observed("main", "a" * 40, "b" * 40,
    Vector(G.DirtyPath('M', ' ', "src/main/scala/Example.scala", None)), Some("d" * 40),
    Some(G.Counts(1, 1)), Some("c" * 40)), Some("d" * 40))
  val before = actual.observed.copy(dirty = Vector(G.DirtyPath('U', 'U', "src/main/scala/Example.scala", None)))
  val prefix = Vector(C.Receipt("index-local", "exited", Some(0), "", ""), C.Receipt("index-remote", "exited", Some(0), "", ""))
  val candidate = N.Payload("PendingMergePrepared", "/specification/repository", before, indexTree = Some("e" * 40),
    after = Some(actual.observed), method = Some("resolved-conflict"), mergeHead = actual.mergeHead, pushContinuation = Some(push))
  val state = C.State(validation.mode, validation.reason, checks, "20", prefix)
  val intent = candidate.copy(stage = "ValidationChecking", validation = Some(state))
  val candidateKey = N.candidateKey(sourceNamespace, "pending-merge")
  val intentKey = N.candidateKey(sourceNamespace, "validation-intent-2")
  val host = Json.obj("schema" -> "normal-resolved-process-plan".asJson, "optionalHostFact" -> Json.Null,
    "programs" -> Json.obj("merge-compile" -> Json.obj("workspace-id" -> "sm-workflow-repository-validation-1".asJson)))
  val target = M.Target("component", "session", context, candidate.root, "/specification/upstream.git", "25",
    "native-repository:session:" + intentKey, sourceNamespace, candidateKey, "20", ContextReference("session:native-" + candidateKey, "1"),
    N.codec.encode(candidate), intentKey, "25", ContextReference("session:native-" + intentKey, "1"), N.codec.encode(intent),
    2, checks.head, checks, prefix, validation, actual, "e" * 40, host, Some(push), None, M.policy)
  val reference = ContextReference("existing-merge-check-human-direction", "2")
  val answer = RepositorySyncValidationContinuation.Answer(M.choices.head, "Authorize one exact same-candidate attempt", true, true)
  val direction = M.Direction(target, answer.choice, answer.rationale, context.participantIdentity, reference, true, true)
  val refusal = M.PreparationRefusal("sm-workflow-repository-validation-1", checks.head.kind,
    "Actual typed workspace admission refused. Diagnostic NotStarted prose is not a process receipt.\n" * 2000)
  val rejected = target.copy(frontierRevision = "26",
    frontierCause = "native-repository:session:" + N.candidateKey(sourceNamespace, "validation-preparation-rejected-2"),
    preparationRefusal = Some(refusal))
  val publicReference = V.WorkflowArtifactReference(reference.identity, reference.revision)
  val execution = V.WorkflowExecutionEvidence(Vector(publicReference), Some(context.participantIdentity), None, None)
  def description(value: M.Target = target): V.RepositorySyncValidationContinuationDescriptionResult =
    V.RepositorySyncValidationContinuationDescriptionResult(value.sessionId, "adoption-required", M.message,
      value.componentInstance, M.targetRecord(value), M.choices, RepositorySyncValidationContinuation.adoptionOperation)
  def adoption(value: M.Target = target): V.RepositorySyncValidationContinuationAdoptionInput =
    V.RepositorySyncValidationContinuationAdoptionInput(value.sessionId, M.targetRecord(value), answer.choice, answer.rationale,
      publicReference, true, true, execution)
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncMergeCheckContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncMergeCheckContinuationFixture.*

  "C01 same-candidate merge check continuation" should {
    "retain the complete selected host plan from string and structured typed configuration" in {
      Given("the same full plan as JSON text and structured ConfigurationValue with opaque keys nulls lists and numbers")
      val structured = ConfigurationValue.ObjectValue(Map(
        "schema" -> ConfigurationValue.StringValue("full-host-plan"),
        "programs" -> ConfigurationValue.ObjectValue(Map("merge-compile:1" -> ConfigurationValue.ObjectValue(Map(
          "workspace-id" -> ConfigurationValue.StringValue("sm-workflow-repository-validation-1"),
          "arguments" -> ConfigurationValue.ListValue(List(ConfigurationValue.StringValue("--batch"), ConfigurationValue.StringValue("compile"))),
          "optional-host-fact" -> ConfigurationValue.NullValue,
          "fixed" -> ConfigurationValue.BooleanValue(true),
          "revision" -> ConfigurationValue.NumberValue(BigDecimal(4))))))))
      val expected = Json.obj("schema" -> "full-host-plan".asJson, "programs" -> Json.obj("merge-compile:1" -> Json.obj(
        "workspace-id" -> "sm-workflow-repository-validation-1".asJson, "arguments" -> Vector("--batch", "compile").asJson,
        "optional-host-fact" -> Json.Null, "fixed" -> Json.True, "revision" -> 4.asJson)))
      val text = ConfigurationValue.StringValue(expected.noSpaces)
      When("the declared typed host-plan decoder reads either configuration representation")
      val decoded = Vector(text, structured).map(value => M.HostPlanDecoder.decode(value).toOption)
      Then("all selected host facts and opaque names remain exact without projection or reconstruction")
      decoded shouldBe Vector(Some(expected), Some(expected))
      decoded.foreach(value => value.get.hcursor.downField("programs").downField("merge-compile:1")
        .downField("optional-host-fact").focus shouldBe Some(Json.Null))
    }

    "refuse malformed and nonobject typed host plans without treating input text as an override" in {
      Given("malformed JSON empty declarations and nonobject configuration values")
      val values = Vector(ConfigurationValue.StringValue("{"), ConfigurationValue.StringValue("null"),
        ConfigurationValue.StringValue("[]"), ConfigurationValue.StringValue("42"), ConfigurationValue.StringValue("{}"),
        ConfigurationValue.ObjectValue(Map.empty), ConfigurationValue.ListValue(List.empty), ConfigurationValue.NullValue)
      When("the same required typed decoder checks each supplied configuration value")
      val decoded = values.map(M.HostPlanDecoder.decode)
      Then("every invalid declaration refuses instead of producing a partial selected plan")
      decoded.foreach(_.isSuccess shouldBe false)
    }

    "roundtrip exact historical intent and typed refusal targets through the existing public contract" in {
      Given("an authored resolved conflict candidate under an original finite push direction and a historical resultless intent")
      val targets = Vector(target, rejected)
      When("canonical storage and public Record projection are decoded by the explicit policy")
      val stored = targets.map(value => M.decodeTarget(value.asJson))
      val public = targets.map(value => M.targetC(M.targetRecord(value)).toOption)
      Then("every candidate byte, original namespace, selected prefix and refusal distinction is preserved")
      stored shouldBe targets.map(Right(_))
      public shouldBe targets.map(Some(_))
      RepositorySyncValidationContinuation.decodeAnyTarget(target.asJson) shouldBe
        Right(RepositorySyncValidationContinuation.MergeCheckTarget(target))
      direction.asJson.as[M.Direction] shouldBe Right(direction)
      target.preparationRefusal shouldBe None
      rejected.preparationRefusal shouldBe Some(refusal)
      target.pushDirection shouldBe Some(push)
      target.pushDirection.get.target.finitePlan shouldBe P.finitePlan
      M.namespace(target) shouldBe "merge-check-continuation-25"
      M.namespace(rejected) shouldBe "merge-check-continuation-26"
    }

    "preserve opaque host keys and historical native nulls across the public Record transport" in {
      Given("a complete host object and original payloads with historical optional null fields")
      val original = target.asJson
      When("Record public names normalize the opaque objects as complete JSON text")
      val normalized = io.circe.parser.parse(take(M.targetRecord(target).toJsonStringC)).toOption.get
      val restored = GoalPhaseNativeMetadata.normalizePublicJson(normalized).flatMap(M.decodeTarget)
      Then("the restored native payloads and process plan equal their original JSON values")
      restored shouldBe Right(target)
      target.candidatePayload.hcursor.downField("payload").downField("validation").focus shouldBe Some(Json.Null)
      restored.toOption.get.candidatePayload shouldBe target.candidatePayload
      restored.toOption.get.intentPayload shouldBe target.intentPayload
      restored.toOption.get.processPlan shouldBe host
      M.decodeTarget(original.mapObject(_.add("processPlan", Json.fromString(host.noSpaces)))) shouldBe Right(target)
    }

    "refuse missing extra null malformed and crossed target meanings without fallback" in {
      Given("the exact manual target and its meaning-bearing canonical fields")
      val wire = target.asJson
      val missing = wire.asObject.get.keys.toVector.map(key => wire.mapObject(_.remove(key)))
      val invalid = missing ++ Vector(wire.mapObject(_.add("unexpected", Json.True)), wire.mapObject(_.add("preparationRefusal", Json.Null)),
        wire.mapObject(_.add("candidatePayload", Json.fromString("[]"))), wire.mapObject(_.add("intentPayload", Json.fromString("{"))),
        target.copy(candidatePayload = target.candidatePayload.mapObject(_.add("unexpectedNativeFact", Json.True))).asJson,
        wire.mapObject(_.add("processPlan", Json.Null)), wire.mapObject(_.add("processPlan", Json.obj())),
        target.copy(continuationPolicy = "unsupported").asJson,
        target.copy(sourceNamespace = "arbitrary-25").asJson,
        target.copy(candidateReference = ContextReference("other:native-" + candidateKey, "1")).asJson,
        target.copy(intentRevision = "24").asJson, target.copy(indexTree = "f" * 40).asJson,
        target.copy(actual = actual.copy(mergeHead = Some("f" * 40))).asJson,
        target.copy(pushDirection = None).asJson,
        target.copy(pushDirection = Some(push.copy(target = push.target.copy(finitePlan = P.finitePlan :+ "another-push")))).asJson,
        rejected.copy(preparationRefusal = Some(refusal.copy(kind = WorkflowSbtValidation.Kind.Test))).asJson)
      When("a supplied field is absent, expanded, contradictory or belongs to another source")
      val decoded = invalid.map(RepositorySyncValidationContinuation.decodeAnyTarget)
      Then("every invalid input refuses while the complete original contract stays available")
      decoded.foreach(_.isLeft shouldBe true)
      M.decodeTarget(wire) shouldBe Right(target)
      M.decodeTarget(rejected.asJson) shouldBe Right(rejected)
    }

    "bind every explicit Human assertion and require exact worker and reference membership" in {
      Given("a public adopting answer, its original participant and one existing direction artifact reference")
      val input = adoption()
      val large = answer.copy(rationale = "独立した判断の全文\n" * 10000)
      When("the existing evidence binding dispatch selects the merge policy")
      val binding = take(M.bindingC(target.componentInstance, target, large.choice, large.rationale,
        context.participantIdentity, reference, true, true))
      val selected = RepositorySyncValidationContinuation.selectBinding(binding)
      Then("the full rationale and exact original scope remain bound without byte limits or inferred consent")
      selected shouldBe Right(binding)
      large.rationale.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
      M.decodeAnswer(large.asJson) shouldBe Right(large)
      M.executionEvidenceValid(input, target) shouldBe true
      M.executionEvidenceValid(input.copy(executionEvidence = execution.copy(references = Vector.empty)), target) shouldBe false
      M.executionEvidenceValid(input.copy(executionEvidence = execution.copy(workerIdentity = Some("other"))), target) shouldBe false
      M.executionEvidenceValid(input.copy(reference = publicReference.copy(revision = "other")), target) shouldBe false
      Vector((false, true), (true, false), (false, false)).foreach { (ended, accepted) =>
        M.bindingC(target.componentInstance, target, answer.choice, answer.rationale,
          context.participantIdentity, reference, ended, accepted).isSuccess shouldBe false
      }
      M.bindingC("other-component", target, answer.choice, answer.rationale, context.participantIdentity, reference, true, true).isSuccess shouldBe false
      M.bindingC(target.componentInstance, target, answer.choice, answer.rationale, "other", reference, true, true).isSuccess shouldBe false
      M.decodeAnswer(answer.copy(choice = "AuthorizeNewValidation").asJson).isLeft shouldBe true
      M.decodeAnswer(answer.asJson.mapObject(_.add("extraConsent", Json.True))).isLeft shouldBe true
    }

    "keep Defer passive and prepare genuine Human input through the existing discriminated route" in {
      Given("the actual-shaped description and authored independent Human input with no provisioned authority")
      val description = RepositorySyncMergeCheckContinuationFixture.description()
      val publicanswer = V.RepositorySyncValidationContinuationAnswer(answer.choice, answer.rationale, true, true)
      val deferred = answer.copy(choice = "Defer", priorExecutionEnded = false, acceptOriginalUncertainty = false)
      val now = java.time.Instant.parse("2026-11-04T00:00:00Z")
      When("normal passive evidence preparation encodes the exact public target and answer")
      val prepared = Preparation.prepareC(description, publicanswer, publicReference, execution, context.participantIdentity, "fixture-independent-host", now)
      val mismatched = Preparation.prepareC(description.copy(choices = RepositorySyncValidationContinuation.choices),
        publicanswer, publicReference, execution, context.participantIdentity, "fixture-independent-host", now)
      Then("correct input encodes and crossed policy refuses while Defer cannot become a persisted authorization")
      prepared.isSuccess shouldBe true
      mismatched.isSuccess shouldBe false
      M.decodeAnswer(deferred.asJson) shouldBe Right(deferred)
      direction.copy(choice = "Defer").asJson.as[M.Direction].isLeft shouldBe true
      M.bindingC(target.componentInstance, target, "Defer", deferred.rationale,
        context.participantIdentity, reference, false, false).isSuccess shouldBe true
    }

    "accept only contiguous successful selected prefix identities and kinds" in {
      Given("both structural successes followed by an authored complete Compile observation")
      val compile = C.Receipt(checks.head.identity, "exited", Some(0), "complete compile output\n" * 5000, "",
        Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)))
      val receipts = prefix :+ compile
      val invalid = Vector(receipts.reverse, receipts.updated(2, compile.copy(identity = "another-check")),
        receipts.updated(2, compile.copy(sbt = None)), receipts.updated(2, compile.copy(exitCode = Some(1))),
        receipts.updated(2, compile.copy(termination = "timed-out", exitCode = None)), receipts.updated(0, prefix.head.copy(identity = "index-remote")))
      When("the production prefix gate compares ordered actual identities and typed selected kinds")
      val valid = M.validPrefix(receipts, checks)
      val crossed = invalid.map(value => M.validPrefix(value, checks))
      Then("only the complete ordered successful Compile prefix can seed the unchanged candidate")
      valid shouldBe true
      crossed shouldBe Vector.fill(invalid.size)(false)
      M.validPrefix(Vector.empty, checks) shouldBe true
      M.validPrefix(prefix, checks) shouldBe true
      compile.stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
    }

    "omit absent new native provenance and reject explicit null or unknown refusal fields" in {
      Given("the historical native candidate without merge-check provenance and a new admitted source")
      val old = N.codec.encode(candidate)
      val continued = candidate.copy(mergeCheckContinuation = Some(direction))
      val refusalwire = refusal.asJson
      When("the native codec restores historical absence and each exact extended field")
      val restored = N.codec.decode(old)
      val next = N.codec.decode(N.codec.encode(continued))
      val nulls = Vector("mergeCheckContinuation", "validationPreparation").map(name => old.mapObject(fields =>
        fields.add("payload", fields("payload").get.mapObject(_.add(name, Json.Null)))))
      Then("old bytes keep their meaning and null cannot erase a direction or counterfeit a refusal")
      restored shouldBe Right(candidate)
      N.codec.encode(restored.toOption.get) shouldBe old
      next shouldBe Right(continued)
      next.toOption.get.validation shouldBe None
      next.toOption.get.pushContinuation shouldBe Some(push)
      nulls.foreach(value => N.codec.decode(value).isLeft shouldBe true)
      Vector(refusalwire.mapObject(_.remove("diagnostic")), refusalwire.mapObject(_.add("exitCode", 0.asJson)),
        refusalwire.mapObject(_.add("process", Json.obj())), refusalwire.mapObject(_.add("diagnostic", Json.Null)))
        .foreach(value => value.as[M.PreparationRefusal].isLeft shouldBe true)
    }

    "store an actual typed admission rejection separately and stop every later selected check" in {
      Given("an authored owning-UoW AdmissionRejected for the first product intent and a successful structural prefix")
      val conclusion = _conclusion(refusal.diagnostic)
      val selected = SbtTarget(take(WorkspaceId.parseC(refusal.workspaceId)))
      val attempt = SbtExecutionAttempt.AdmissionRejected(selected, SbtOperation.Compile, conclusion)
      When("the production attempt mapper creates the payload saved by the ordinary check loop")
      val payload = take(C.productAttemptPayloadC(intent, checks.head, attempt))
      val stored = N.codec.decode(N.codec.encode(payload)).toOption.get
      val nextchecks = (2 until checks.size + 2).map(index => C.mayRunCheck(stored, index, merge = true))
      Then("only the full typed refusal is retained; no process result exit code phantom success or later check is created")
      stored.stage shouldBe "ValidationPreparationRejected"
      stored.validation shouldBe intent.validation
      stored.validation.get.receipts shouldBe prefix
      stored.validationPreparation shouldBe Some(refusal.copy(diagnostic = conclusion.show))
      stored.mergeCheckContinuation shouldBe None
      stored.pushContinuation shouldBe Some(push)
      nextchecks shouldBe Vector.fill(checks.size)(false)
      stored.validationPreparation.get.asJson.asObject.get.keys.toSet shouldBe Set("workspaceId", "kind", "diagnostic")
      N.codec.encode(intent).hcursor.downField("payload").downField("validationPreparation").focus shouldBe None
      C.mayRunCheck(intent, 3, merge = true) shouldBe false
      C.mayRunCheck(intent, 2, merge = true) shouldBe true
      stored.validationPreparation.get.diagnostic.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
    }

    "refuse crossed typed rejections and keep uncertain attempts without fabricated preparation proof" in {
      Given("typed UoW outcomes with changed workspace or operation and an actual uncertainty diagnostic")
      val conclusion = _conclusion("The actual typed attempt is uncertain; prose saying NotStarted grants no retry")
      val workspace = SbtTarget(take(WorkspaceId.parseC(refusal.workspaceId)))
      val other = SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-2")))
      val outcomes = Vector(SbtExecutionAttempt.AdmissionRejected(other, SbtOperation.Compile, conclusion),
        SbtExecutionAttempt.AdmissionRejected(workspace, SbtOperation.Test, conclusion),
        SbtExecutionAttempt.Uncertain(workspace, SbtOperation.Compile, conclusion))
      When("the exact same production mapper consumes these outcomes")
      val mapped = outcomes.map(value => C.productAttemptPayloadC(intent, checks.head, value))
      Then("none can append a selected receipt or typed preparation refusal and the original resultless intent stays intact")
      mapped.foreach(_.isSuccess shouldBe false)
      M.preparationRefusalC(checks.head, outcomes.last).isSuccess shouldBe false
      intent.validationPreparation shouldBe None
      intent.validation.get.receipts shouldBe prefix
      N.codec.decode(N.codec.encode(intent)) shouldBe Right(intent)
    }

    "retain an observed real failed process as an ordinary failed receipt rather than a preparation refusal" in {
      Given("an authored actual-shaped returned Compile process with exit code seven")
      val empty = ProcessExecutionCapture(Vector.empty, 0, false)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(7), empty, empty, Vector.empty, 1, "fixture-compile")
      val observed = SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(refusal.workspaceId))), SbtOperation.Compile, process))
      When("the owning-loop attempt mapper handles the returned process")
      val payload = take(C.productAttemptPayloadC(intent, checks.head, observed))
      Then("the genuine failure remains attributable for ordinary repair and never becomes missing-result or admission-rejected evidence")
      payload.stage shouldBe "ValidationChecking"
      payload.validationPreparation shouldBe None
      payload.validation.get.receipts.take(2) shouldBe prefix
      payload.validation.get.receipts.last.exitCode shouldBe Some(7)
      payload.validation.get.receipts.last.processObservation shouldBe Some(process)
      RepositorySyncFormalFailureContinuation.completeFailure(payload.validation.get.receipts.last, checks.head) shouldBe true
      C.mayRunCheck(payload, 3, merge = true) shouldBe false
      M.validPrefix(payload.validation.get.receipts, checks) shouldBe false
    }
  }

  private def _conclusion(message: String): Conclusion = Consequence.stateConflict[Unit](message) match {
    case Consequence.Failure(value) => value
    case _ => fail("Expected an authored diagnostic conclusion")
  }
}
