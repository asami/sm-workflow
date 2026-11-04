package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.{Argument, Request}
import org.simplemodeling.textus.smworkflow.value as V
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncFormalFailureContinuation as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncManagedGitObservation as G
import RepositorySyncPurpose as P

private[admission] object RepositorySyncFormalFailureContinuationFixture {
  val checks = Vector(C.Check("first-product", 1), C.Check("failed-product", 2))
  val policy = C.Policy("actual-selected-checks", 1, "required", "Keep selected order", checks)
  val successful = C.Receipt("first-product", "exited", Some(0), "complete successful output\n", "")
  val failed = C.Receipt("failed-product", "exited", Some(7), "complete stdout\n", "actual host failure\n" * 10000)
  val prefix = Vector(C.Receipt("index-local", "exited", Some(0), "", ""),
    C.Receipt("index-remote", "exited", Some(0), "", ""), successful)
  val actual = G.Native(RepositorySyncValidationContinuationFixture.observed.copy(counts = Some(G.Counts(1, 1))), Some("c" * 40))
  val target = N.Target("component", "session", RepositorySyncValidationContinuationFixture.context,
    "/specification/repository", "/specification/upstream.git", "12", "native-repository:session:validation-result-3",
    "", "pending-merge", "4", "validation-result-3", "12", "validation-intent-3", "11",
    ContextReference("session:native-pending-merge", "1"), ContextReference("session:native-validation-result-3", "1"),
    ContextReference("session:native-validation-intent-3", "1"), 1, checks(1), checks, prefix, failed, policy,
    Some(C.State(policy.mode, policy.reason, checks, "4", prefix :+ failed)), None, None, actual, "b" * 40,
    Json.obj("schema" -> Json.fromString("normal-process-plan"), "optionalHostFact" -> Json.Null), None, N.policy)
  val request = RepositorySyncPurposeContentContinuationFixture.original
  val candidate = P.Candidate("a" * 40, "b" * 40, "d" * 40, "e" * 40, "f" * 40,
    Vector(P.Content(request.files.head.path, Some("new"))), Vector(P.Content(request.files.head.path, Some("old"))), "Retain purpose")
  val progress = P.Progress(request, Some(P.Result("resolved", "Retain purpose", Vector.empty, Vector.empty)),
    candidate = Some(candidate), receipts = (prefix.take(1) ++ prefix.drop(2)) :+ failed,
    selection = Some(RepositorySyncProgramImpact.Selection("FULL", "actual purpose checks", checks, Vector.empty)))
  val slot = P.Slot("purpose-slot-1", checks, GoalPhaseStepAssessment.Policy("purpose-checks", "code", "required", "off", "off", Vector.empty))
  val purpose = target.copy(candidateKey = "purpose-1-prepared", candidateReference = ContextReference("session:native-purpose-1-prepared", "1"),
    failureKey = "purpose-1-check-1-result", failureReference = ContextReference("session:native-purpose-1-check-1-result", "1"),
    intentKey = "purpose-1-check-1-intent", intentReference = ContextReference("session:native-purpose-1-check-1-intent", "1"),
    successfulPrefix = prefix.take(1) ++ prefix.drop(2), mergeState = None, purpose = Some(progress), purposeSlot = Some(slot))
  val selectedTest = C.Check("selected-real-test", 3, WorkflowSbtValidation.Kind.TestOnly)
  val corrected = purpose.copy(validationPolicy = policy.copy(checks = checks :+ selectedTest),
    purposeSlot = Some(slot.copy(checks = checks :+ selectedTest)), continuationPolicy = N.selectionPolicy,
    proposedChecks = Some(checks :+ selectedTest))
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncFormalFailureContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncFormalFailureContinuationFixture.*
  "RS-FORMAL-02 explicit new attempt" should {
    "decode inline and Resource adoption through the same strict complete business contract" in {
      Given("the existing public adoption DTO and one deferred local Resource")
      val reference = V.WorkflowArtifactReference("selection-contract-direction", "1")
      val input = V.RepositorySyncValidationContinuationAdoptionInput("session", N.publicRecord(corrected),
        N.selectionChoices.head, "Keep the exact fixed original checks and append selected tests", reference, false, false,
        V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None))
      val raw = input.toRecord().toJsonStringC.toOption.get
      def request(fields: (String, Any)*) = Request.of(component = "sm-workflow", service = "RepositorySyncService",
        operation = RepositorySyncValidationContinuation.adoptionOperation,
        arguments = fields.toList.map((name, value) => Argument(name, value)), properties = Nil, switches = Nil)
      val inline = RepositorySyncValidationContinuation.adoptionSourceC(request("input" -> raw)).toOption.get
      val resource = RepositorySyncValidationContinuation.adoptionSourceC(request("inputResource" -> "file:///private/tmp/selection-contract.json")).toOption.get
      var reads = 0
      When("each exclusive transport reaches the same strict decoder")
      val a = RepositorySyncValidationContinuation.readAdoptionInputC(inline, _ => fail("Inline cannot read a Resource"))
      val b = RepositorySyncValidationContinuation.readAdoptionInputC(resource, _ => {
        reads += 1
        org.goldenport.Consequence.success(raw)
      })
      Then("the exact complete typed input survives and Resource is read once")
      a.toOption shouldBe Some(input)
      b.toOption shouldBe Some(input)
      reads shouldBe 1
      Vector(request(), request("input" -> raw, "inputResource" -> "file:///private/tmp/a"),
        request("input" -> "{"), request("inputResource" -> "https://example.invalid/a"))
        .foreach(value => RepositorySyncValidationContinuation.adoptionSourceC(value).isSuccess shouldBe false)
      val wire = io.circe.parser.parse(raw).toOption.get
      val invalid = Vector("{", "null", "[]", raw.dropRight(1) + ",\"sessionId\":\"duplicate\"}",
        wire.mapObject(_.add("unexpected", Json.True)).noSpaces,
        wire.mapObject(_.add("reference", Json.Null)).noSpaces,
        wire.mapObject(fields => fields.add("executionEvidence", fields("executionEvidence")
          .orElse(fields("execution_evidence")).getOrElse(Json.obj()).mapObject(_.add("unexpected", Json.True)))).noSpaces)
      invalid.foreach { text =>
        RepositorySyncValidationContinuation.adoptionSourceC(request("input" -> text)).isSuccess shouldBe false
        RepositorySyncValidationContinuation.readAdoptionInputC(resource, _ => org.goldenport.Consequence.success(text)).isSuccess shouldBe false
      }
      RepositorySyncValidationContinuation.readAdoptionInputC(resource, _ => org.goldenport.Consequence.resourceInvalid(
        "Normal finite Resource read refused")).isSuccess shouldBe false
    }
    "retain canonical v1 and bind v2 to an exact fixed TestOnly append" in {
      Given("an original failed selection and one applicable registered TestOnly check")
      When("both explicit policy versions use the existing target and evidence protocol")
      N.decodeTarget(target.asJson) shouldBe Right(target)
      target.asJson.hcursor.downField("proposedChecks").focus shouldBe None
      N.decodeTarget(corrected.asJson) shouldBe Right(corrected)
      RepositorySyncValidationContinuation.decodeAnyTarget(corrected.asJson) shouldBe
        Right(RepositorySyncValidationContinuation.FormalFailureTarget(corrected))
      val reference = ContextReference("selection-correction", "1")
      val rationale = "Append the actual fixed TestOnly coverage on the unchanged candidate"
      val binding = N.bindingC("component", corrected, N.selectionChoices.head, rationale, "participant", reference, false, false)
      Then("the whole proposal is bound and each policy accepts only its own adopting choice")
      binding.isSuccess shouldBe true
      RepositorySyncValidationContinuation.selectBinding(binding.toOption.get) shouldBe Right(binding.toOption.get)
      N.bindingC("component", corrected, N.choices.head, rationale, "participant", reference, false, false).isSuccess shouldBe false
      N.bindingC("component", purpose, N.selectionChoices.head, rationale, "participant", reference, false, false).isSuccess shouldBe false
      val answer = RepositorySyncValidationContinuation.Answer(N.selectionChoices.head, rationale, false, false)
      N.decodeAnswer(corrected, answer.asJson) shouldBe Right(answer)
      N.decodeAnswer(purpose, answer.asJson).isLeft shouldBe true
      val description = V.RepositorySyncValidationContinuationDescriptionResult("session", "adoption-required", N.message,
        "component", N.publicRecord(corrected), N.selectionChoices, RepositorySyncValidationContinuation.adoptionOperation)
      val publicReference = V.WorkflowArtifactReference(reference.identity, reference.revision)
      val execution = V.WorkflowExecutionEvidence(Vector(publicReference), Some("participant"), None, None)
      val publicAnswer = V.RepositorySyncValidationContinuationAnswer(answer.choice, answer.rationale, false, false)
      val now = java.time.Instant.parse("2026-11-03T00:00:00Z")
      org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryValidationContinuationPreparation.prepareC(
        description, publicAnswer, publicReference, execution, "participant", "normal-development-host", now).isSuccess shouldBe true
      org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryValidationContinuationPreparation.prepareC(
        description.copy(choices = N.choices), publicAnswer, publicReference, execution, "participant", "normal-development-host", now).isSuccess shouldBe false
      org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryValidationContinuationPreparation.prepareC(
        description, publicAnswer.copy(choice = N.choices.head), publicReference, execution, "participant", "normal-development-host", now).isSuccess shouldBe false
      N.proposedChecks(checks, policy, Some(slot)) shouldBe None
      N.proposedChecks(checks :+ selectedTest, corrected.validationPolicy, corrected.purposeSlot) shouldBe None
      val proposed = corrected.proposedChecks.get
      val invalid = Vector(corrected.copy(proposedChecks = None), corrected.copy(proposedChecks = Some(checks)),
        corrected.copy(proposedChecks = Some(proposed.tail)), corrected.copy(proposedChecks = Some(proposed.reverse)),
        corrected.copy(proposedChecks = Some(proposed.updated(0, checks.head.copy(ordinal = 4)))),
        corrected.copy(proposedChecks = Some(proposed :+ selectedTest)),
        corrected.copy(proposedChecks = Some(checks :+ selectedTest.copy(kind = WorkflowSbtValidation.Kind.Test))),
        corrected.copy(proposedChecks = Some(checks :+ selectedTest.copy(identity = "arbitrary-test"))),
        corrected.copy(purposeSlot = Some(slot)), corrected.copy(continuationPolicy = N.policy),
        target.copy(continuationPolicy = N.selectionPolicy, proposedChecks = Some(proposed)))
      invalid.foreach(value => N.decodeTarget(value.asJson).isLeft shouldBe true)
      Vector(corrected.asJson.mapObject(_.add("proposedChecks", Json.Null)),
        corrected.asJson.mapObject(_.add("unexpected", Json.True)),
        purpose.asJson.mapObject(_.add("proposedChecks", proposed.asJson))).foreach(value => N.decodeTarget(value).isLeft shouldBe true)
    }
    "authenticate corrected progress solely against its actual exact stored direction" in {
      Given("a selection direction that retains the original failed batch and original successful prefix")
      val direction = N.Direction(corrected, N.selectionChoices.head, "Require actual selected TestOnly coverage",
        "participant", ContextReference("selection-correction", "1"), false, false)
      val original = corrected.purpose.get
      When("the new progress changes only selection receipts and host direction")
      val next = original.copy(selection = N.attemptSelection(corrected), receipts = corrected.successfulPrefix,
        assessment = None, review = None, hostContinuation = Some(direction))
      Then("the original selection failure candidate and repair accounting stay immutable")
      N.validPurposeAttempt(next, direction) shouldBe true
      next.selection.get.checks shouldBe corrected.proposedChecks.get
      original.selection.get.checks shouldBe corrected.selectedChecks
      original.receipts.last shouldBe corrected.failed
      Vector(next.copy(selection = original.selection), next.copy(candidate = original.candidate.map(_.copy(tree = "f" * 40))),
        next.copy(attempt = original.attempt + 1), next.copy(commits = Vector("f" * 40)),
        next.copy(receipts = Vector.empty)).foreach(value => N.validPurposeAttempt(value, direction) shouldBe false)
      next.asJson.as[P.Progress] shouldBe Right(next)
    }
    "preserve opaque host-map keys nulls and nested prior directions through the real public Record roundtrip" in {
      Given("merge and Purpose targets containing a declaration-shaped host plan and a fixture-owned prior direction")
      val hostplan = Json.obj(
        "schema" -> "cncf.runtime-process-execution.v2".asJson,
        "programs" -> Json.arr(),
        "workspaces" -> Json.arr(Json.obj("identity" -> "product-checks".asJson, "root" -> "/specification/repository".asJson)),
        "workspaceGrants" -> Json.arr(Json.obj("componentId" -> "component".asJson,
          "workspaceId" -> "product-checks".asJson, "access" -> "manage".asJson)),
        "grants" -> Json.arr(Json.obj("componentId" -> "component".asJson, "capabilities" -> Vector("product-test").asJson)),
        "sbtPrograms" -> Json.arr(Json.obj("target" -> "product-checks".asJson, "operation" -> "test".asJson,
          "identity" -> "product-test".asJson, "capability" -> "product-test".asJson,
          "sbtExecutable" -> "/specification/tools/sbt".asJson,
          "environment" -> Json.obj("JAVA_HOME" -> "/specification/jdk".asJson, "HOME" -> "/specification/home".asJson,
            "PATH" -> "/specification/jdk/bin:/usr/bin:/bin".asJson, "MiXeD_Key" -> "exact value".asJson,
            "tool.setting-With:Case" -> "opaque punctuation".asJson),
          "limits" -> Json.obj("launchTimeoutMillis" -> 10000.asJson, "executionTimeoutMillis" -> 180000.asJson,
            "terminationGraceMillis" -> 1000.asJson, "stdinBytes" -> 1048576.asJson, "stdoutBytes" -> 1048576.asJson,
            "stderrBytes" -> 16384.asJson, "argumentCount" -> 16.asJson, "argumentBytes" -> 131072.asJson,
            "artifactCount" -> 1.asJson, "artifactBytes" -> 131072.asJson, "workAreaBytes" -> 4194304.asJson))),
        "opaqueHostFacts" -> Json.obj("NullableFact" -> Json.Null,
          "nested.Array-With:Case" -> Json.arr(Json.obj("JAVA_HOME" -> "nested value".asJson, "HOME" -> Json.Null),
            Json.arr("PATH".asJson, Json.Null, Json.obj("MixedCASE.Key" -> true.asJson))),
          "processPlan" -> Json.obj("Preserve_This:Key" -> Json.Null)))
      val merge = target.copy(processPlan = hostplan)
      val selectedpurpose = purpose.copy(processPlan = hostplan,
        purpose = purpose.purpose.map(progress => progress.copy(request = progress.request.copy(records = Vector.empty))))
      val previous = N.Direction(selectedpurpose, N.choices.head, "Fixture-owned prior host correction", "participant",
        ContextReference("prior-host-correction", "1"), false, false)
      val nestedprogress = selectedpurpose.purpose.get.copy(hostContinuation = Some(previous))
      val failurekey = RepositorySyncPurposeRuntime.attemptPrefix(nestedprogress) + "-check-1-result"
      val intentkey = RepositorySyncPurposeRuntime.attemptPrefix(nestedprogress) + "-check-1-intent"
      val nested = selectedpurpose.copy(purpose = Some(nestedprogress), frontierRevision = "14",
        frontierCause = "native-repository:session:" + failurekey,
        failureKey = failurekey, failureRevision = "14", failureReference = ContextReference("session:native-" + failurekey, "1"),
        intentKey = intentkey, intentRevision = "13", intentReference = ContextReference("session:native-" + intentkey, "1"))
      val targets = Vector(merge, selectedpurpose, nested)
      Vector("merge", "Purpose", "Purpose with prior host direction").zip(targets).foreach { (label, value) =>
        withClue(label + " fixture must satisfy the exact target contract: ") {
          N.validTarget(value) shouldBe true
        }
      }
      When("publicRecord passes each target through Record JSON serialization public-name normalization and target decoding")
      val projected = targets.map(value => for {
        raw <- N.publicRecord(value).toJsonStringC.toOption.toRight("Public Record serialization failed")
        parsed <- io.circe.parser.parse(raw).left.map(_.message)
        normalized <- GoalPhaseNativeMetadata.normalizePublicJson(parsed)
        decoded <- N.decodeTarget(normalized)
      } yield normalized -> decoded)
      Then("the public text restores the exact host object while internal storage and all nested prior direction facts remain unchanged")
      projected.zip(targets).foreach { (result, expected) =>
        result.map(_._2) shouldBe Right(expected)
        val normalized = result.toOption.get._1
        normalized.hcursor.get[String]("processPlan").toOption shouldBe Some(hostplan.noSpaces)
        expected.asJson.hcursor.downField("processPlan").focus shouldBe Some(hostplan)
        N.decodeTarget(expected.asJson) shouldBe Right(expected)
      }
      val nestedwire = projected.last.toOption.get._1
      nestedwire.hcursor.downField("purpose").downField("hostContinuation").downField("target")
        .get[String]("processPlan").toOption shouldBe Some(hostplan.noSpaces)
      projected.last.toOption.get._2.purpose.get.hostContinuation shouldBe Some(previous)
      projected.last.toOption.get._2.purpose.get.hostContinuation.get.target.processPlan shouldBe hostplan
    }
    "refuse malformed nonobject or null host-plan text while preserving strict target canonicality" in {
      Given("the original canonical stored target and malformed public host-plan representations")
      val wire = target.asJson
      val invalidplans = Vector(Json.fromString("{"), Json.fromString("null"), Json.fromString("[]"),
        Json.fromString("true"), Json.fromString("42"), Json.fromString("\"scalar\""), Json.fromString("{}"),
        Json.Null, Json.arr(), Json.True, Json.fromInt(42))
      val malformed = invalidplans.map(plan => wire.mapObject(_.add("processPlan", plan))) ++ Vector(
        wire.mapObject(_.remove("processPlan")), wire.mapObject(_.add("unexpectedTargetField", Json.True)),
        wire.mapObject(_.add("pending", Json.Null)), Json.Null)
      val previous = N.Direction(purpose.copy(processPlan = Json.Null), N.choices.head, "Invalid prior host fixture",
        "participant", ContextReference("prior-host-correction", "1"), false, false)
      val nested = purpose.copy(purpose = purpose.purpose.map(_.copy(hostContinuation = Some(previous))))
      When("the target decoder restores public host fields before applying the unchanged canonical contract")
      val restored = N.decodeTarget(wire.mapObject(_.add("processPlan", Json.fromString(target.processPlan.noSpaces))))
      val refused = malformed.map(N.decodeTarget) :+ N.decodeTarget(nested.asJson)
      Then("valid text restores its historical object exactly and invalid plans nulls and extra target fields all refuse")
      restored shouldBe Right(target)
      N.decodeTarget(wire) shouldBe Right(target)
      refused.foreach(_.isLeft shouldBe true)
    }
    "retain full diagnostics and successful prefixes for merge and Purpose targets" in {
      Given("an actual formal failure with complete large stderr and successful preceding checks")
      When("both explicitly discriminated target contracts are encoded")
      val targets = Vector(target, purpose)
      Then("neither the old failure nor the successful prefix becomes UNKNOWN or an accepted repair")
      targets.foreach(value => {
        N.decodeTarget(value.asJson) shouldBe Right(value)
        value.failed.termination shouldBe "exited"
        value.failed.exitCode shouldBe Some(7)
        value.failed.stderr shouldBe failed.stderr
        N.namespace(value) shouldBe "formal-failure-12"
      })
      failed.stderr.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
    }
    "refuse UNKNOWN structural missing mismatched and failed prefixes" in {
      Given("a selected product check whose failed result must be formally attributable")
      When("termination or the retained selection and prefix is changed")
      val invalid = Vector(target.copy(failed = failed.copy(termination = "termination-unconfirmed", exitCode = None)),
        target.copy(failed = failed.copy(identity = "index-local")), target.copy(successfulPrefix = prefix.tail),
        target.copy(successfulPrefix = prefix.updated(2, successful.copy(exitCode = Some(1)))),
        target.copy(selectedChecks = checks.reverse), target.copy(intentRevision = "10"))
      Then("none can authorize success, no-op repair or a retry of UNKNOWN work")
      invalid.foreach(value => N.decodeTarget(value.asJson).isLeft shouldBe true)
    }
    "bind the complete current host plan candidate receipt and selection without claiming historical host differences" in {
      Given("a strict target and independent Human direction")
      val reference = ContextReference("host-correction-direction", "1")
      val answer = RepositorySyncValidationContinuation.Answer(N.choices.head, "Host correction observed by the specification", false, false)
      When("the complete evidence binding is selected")
      val binding = N.bindingC("component", target, answer.choice, answer.rationale, "participant", reference, false, false).toOption.get
      Then("crossed full targets have distinct bindings and inapplicable old-worker assertions refuse")
      RepositorySyncValidationContinuation.selectBinding(binding) shouldBe Right(binding)
      Vector(target.copy(processPlan = Json.obj("schema" -> Json.fromString("changed-plan"))),
        target.copy(indexTree = "f" * 40), target.copy(failed = failed.copy(stderr = "changed receipt")))
        .foreach(changed => N.bindingC("component", changed, answer.choice, answer.rationale, "participant", reference,
          false, false).toOption.foreach(_ should not be binding))
      N.bindingC("component", target, answer.choice, answer.rationale, "participant", reference, true, true).isSuccess shouldBe false
      N.decodeTarget(target.asJson.mapObject(_.add("pending", Json.Null))).isLeft shouldBe true
      N.decodeTarget(target.asJson.mapObject(_.add("unknownEffectWaiver", Json.True))).isLeft shouldBe true
      RepositorySyncValidationContinuation.decodeAnyTarget(target.asJson.mapObject(_.add("continuationPolicy", "unsupported".asJson))).isLeft shouldBe true
    }
  }
}
