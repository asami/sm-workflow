package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
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
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncFormalFailureContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncFormalFailureContinuationFixture.*
  "RS-FORMAL-02 explicit new attempt" should {
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
