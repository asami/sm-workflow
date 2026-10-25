package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{ProcessExecutionCapture, ProcessExecutionResult, ProcessExecutionTermination}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncNativeMergeValidation as V
import RepositorySyncNativeMergeReview as R
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedFetch as Fetch
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Actual selected facts, not policy metadata or re-encoded body certificates.
  * @since   Oct.  8, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncNativeValidationApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val policy = V.Policy("original-policy", 1, "required", "Original selected rationale", Vector(V.Check("focused", 1)))
  private val receipts = Vector("index-local", "index-remote", "focused").map(id => V.Receipt(id, "exited", Some(0), "", ""))
  private val state = V.State(policy.mode, policy.reason, policy.checks, "3", receipts)
  private val before = G.Observed("main", "a" * 40, "b" * 40, Vector.empty, Some("c" * 40), Some(G.Counts(1, 1)))
  private val input = R.Request("selected-instance", "7", before.head, "c" * 40, "d" * 40,
    Vector("one.scala"), "Original local diff", "Original remote diff", state.mode, state.reason,
    state.selected.map(_.identity), state.receipts)
  private def nested(value: Json) = value.hcursor.downField("payload").focus.get
  private def replace(value: Json, body: Json) = value.mapObject(_.add("payload", body))

  "Native validation facts" should {
    "refuse replacement of a selected Test binding by TestOnly while preserving old Test receipts" in {
      Given("a saved current-schema Test selection with an absent kind and five-field product receipt")
      val restored = V.stateCodec.decode(V.stateCodec.encode(state)).toOption.get
      val replacement = policy.copy(checks = Vector(V.Check("focused", 1, WorkflowSbtValidation.Kind.TestOnly)))
      When("current policy resolution consumes identity ordinal and kind against the retained selection")
      val compatible = V.applies(restored, policy.copy(identity = "other-metadata", revision = 9,
        checks = policy.checks :+ V.Check("unselected", 2, WorkflowSbtValidation.Kind.TestOnly)))
      val replaced = V.applies(restored, replacement)
      Then("metadata and unused bindings are inert, but a changed selected kind cannot promote or replay the old Test")
      compatible shouldBe true
      replaced shouldBe false
      restored shouldBe state
      V.successful(restored.receipts, selected = restored.selected) shouldBe true
      V.successful(restored.receipts, selected = replacement.checks) shouldBe false
      restored.receipts.last.sbt shouldBe None
      V.productSuccessful(restored.receipts.last, WorkflowSbtValidation.Kind.TestOnly) shouldBe false
    }

    "consume current selected facts and ignore unused annotations without policy extraction" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E1; an actual selected validation payload")
      val saved = N.Payload("MergeValidated", "/selected/repository", before,
        indexTree = Some("d" * 40), after = Some(before), mergeHead = Some("c" * 40), validation = Some(state))
      val wire = N.codec.encode(saved)
      val facts = nested(wire).hcursor.downField("validation").focus.get
      When("current output is produced and independently constructed diagnostic fields are present")
      val decorated = facts.mapObject(_.add("approve", Json.True).add("policy", Json.False)
        .add("selected", facts.hcursor.downField("selected").focus.get.mapArray(_.map(_.mapObject(_.add("command", Json.True)))))
        .add("receipts", facts.hcursor.downField("receipts").focus.get.mapArray(_.map(_.mapObject(_.add("authority", Json.Null))))))
      val decoded = V.stateCodec.decode(decorated)
      Then("actual facts decode without a policy copy or annotation becoming authority")
      decoded shouldBe Right(state)
      N.codec.decode(wire) shouldBe Right(saved)
      facts.asObject.get.keys.toSet should not contain "policy"
      state.productElementNames.toSet should not contain "policy"

      Given("the same current check state with required facts absent or mistyped")
      When("the typed consumer decodes required check fields")
      val wrongCheck = facts.mapObject(_.add("selected", Json.arr(Json.obj("identity" -> Json.fromString("focused"),
        "ordinal" -> Json.True))))
      Then("missing required collections and wrong ordinal types fail without a shape verifier")
      V.stateCodec.decode(facts.mapObject(_.remove("selected"))).isLeft shouldBe true
      V.stateCodec.decode(wrongCheck).isLeft shouldBe true
    }
    "resolve selected executable bindings without unchanged policy identity revision or complete declaration equality" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E2; original selected checks and current bindings")
      When("metadata changes or an extra unselected binding is installed")
      V.applies(state, policy.copy(identity = "different-metadata", revision = 8, reason = "Different projection",
        checks = policy.checks :+ V.Check("unselected", 2))) shouldBe true
      Then("a missing or incompatible selected binding or an actually different skip/required mode is refused")
      V.applies(state, policy.copy(checks = Vector.empty)) shouldBe false
      V.applies(state, policy.copy(checks = Vector(V.Check("focused", 2)))) shouldBe false
      V.applies(state, policy.copy(mode = "explicit-no-product-tests", checks = Vector.empty)) shouldBe false
    }
    "admit actual source and result coverage without a diagnostic observation witness" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E9; actual adjacent native frontiers")
      val initial = InstanceRecord(InstanceIdentity("validation-frontier"), take(RepositorySyncDefinitionBinding.bindC), initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference("owned-correlation"), CausationReference("owned-start"), None, None, None, Vector.empty)
      def next(record: InstanceRecord, stage: String) = take(record.appendC(record.revision,
        HistoryEntry(HistorySequence(record.history.size.toLong + 1), record.revision.next, Lifecycle.Active,
          Some(ProgressionReference(stage)), record.correlation, CausationReference(stage), None, None, None)))
      val sourceRecord = next(next(next(initial, "sync.Fetched"), "sync.Integrated"), "sync.PendingMergePrepared")
      val resultRecord = next(sourceRecord, "sync.MergeValidated")
      val source = N.Stage(sourceRecord, N.Payload("PendingMergePrepared", "/selected/repository", before,
        indexTree = Some("d" * 40), after = Some(before), mergeHead = Some("c" * 40)))
      val selected = state.copy(sourceRevision = sourceRecord.revision.value.toString)
      val result = N.Stage(resultRecord, source.payload.copy(stage = "MergeValidated", validation = Some(selected),
        after = Some(before.copy(counts = Some(G.Counts(9, 9)), mergeBase = Some("e" * 40)))))
      When("selected execution facts cover the actual source and current bindings despite changed observation diagnostics")
      take(V.completedC(result, source, policy.copy(identity = "other-metadata", revision = 8), merge = true)) shouldBe ()
      Then("failed or missing actual results cannot become acceptance; source copies are not acceptance certificates")
      Vector(selected.copy(receipts = selected.receipts.dropRight(1)),
        selected.copy(receipts = selected.receipts.updated(2, selected.receipts(2).copy(exitCode = Some(1))))).foreach { contradicted =>
        V.completedC(result.copy(payload = result.payload.copy(validation = Some(contradicted))), source, policy, merge = true) shouldBe a[Consequence.Failure[?]]
      }
      Given("Spec: docs/spec/repository-sync-native-merge-repair.md; Rule: R6; Example: C1; existing E9 retained checks belong to another actual candidate source")
      val crossed = result.copy(payload = result.payload.copy(validation = Some(selected.copy(sourceRevision = "99"))))
      When("completedC consumes the retained checks against the actual selected source")
      val completed = V.completedC(crossed, source, policy, merge = true)
      Then("checks from another candidate source are refused")
      completed shouldBe a[Consequence.Failure[?]]
    }
  }

  "Historical structural Git Receipt compatibility" should {
    "roundtrip and reuse the exact old State and five-field Receipt schema without execution" in {
      Given("R1: an ordinary pre-migration validation State with its original structural and product receipts")
      val old = Json.obj("mode" -> Json.fromString("required"), "reason" -> Json.fromString(policy.reason),
        "selected" -> Json.arr(Json.obj("identity" -> Json.fromString("focused"), "ordinal" -> Json.fromInt(1))),
        "sourceRevision" -> Json.fromString("3"), "receipts" -> Json.fromValues(receipts.map(receipt => Json.obj(
          "identity" -> Json.fromString(receipt.identity), "termination" -> Json.fromString(receipt.termination),
          "exitCode" -> Json.fromInt(receipt.exitCode.get), "stdout" -> Json.fromString(receipt.stdout),
          "stderr" -> Json.fromString(receipt.stderr)))))
      When("the existing codec reads those historical facts and the current acceptance policy reuses them")
      val restored = V.stateCodec.decode(old).toOption.get
      val accepted = V.successful(restored.receipts, merge = true)
      Then("the exact original schema and selected facts survive without a process request or policy version")
      restored shouldBe state
      V.stateCodec.encode(restored) shouldBe old
      accepted shouldBe true
      V.successful(V.stateCodec.decode(V.stateCodec.encode(restored)).toOption.get.receipts, merge = true) shouldBe true
      old.hcursor.downField("receipts").focus.get.asArray.get.foreach(receipt =>
        receipt.asObject.get.keys.toSet shouldBe Set("identity", "termination", "exitCode", "stdout", "stderr"))
    }

    "preserve clean single and inherited findings while rejecting shared opaque findings" in {
      Given("R2: clean actual-exit receipts and complete historical exit-two structural findings")
      val clean = V.Receipt("index-local", "exited", Some(0), "", "")
      val local = clean.copy(exitCode = Some(2), stdout = "one.scala:7: trailing whitespace\r\n+ context\n")
      val remote = clean.copy(identity = "index-remote", exitCode = Some(2), stdout = "other.scala:8: whitespace\n")
      val product = V.Receipt("focused", "exited", Some(0), "Actual product output", "")
      When("the unchanged application policy compares CNCF-decoded parent finding sets")
      val cleanmerge = V.successful(Vector(clean, clean.copy(identity = "index-remote"), product))
      val single = V.successful(Vector(local))
      val nonmerge = V.successful(Vector(local, product), merge = false)
      val inherited = V.successful(Vector(local, clean.copy(identity = "index-remote"), product))
      val independent = V.successful(Vector(local, remote, product))
      val shared = V.successful(Vector(local, remote.copy(stdout = local.stdout), product))
      Then("a single structural sample retains its old acceptance and only shared parent findings block a merge")
      cleanmerge shouldBe true
      single shouldBe true
      nonmerge shouldBe true
      inherited shouldBe true
      independent shouldBe true
      shared shouldBe false
      V.successful(Vector(clean, clean.copy(identity = "index-remote"), product.copy(exitCode = Some(7)))) shouldBe false
    }

    "refuse unsupported malformed missing and overbound retained structural receipts" in {
      Given("R3: the historical schema can contain failed indeterminate incomplete or contradictory stored values")
      val clean = V.Receipt("index-local", "exited", Some(0), "", "")
      val invalid = Vector(clean.copy(termination = "indeterminate", exitCode = None),
        clean.copy(exitCode = None), clean.copy(exitCode = null), clean.copy(stdout = null), clean.copy(stderr = null),
        clean.copy(stdout = "unexpected"), clean.copy(stderr = "unexpected"), clean.copy(stdout = "\u0000"),
        clean.copy(stdout = "\ud800"), clean.copy(exitCode = Some(2), stdout = "+ source only\n"),
        clean.copy(exitCode = Some(2)), clean.copy(exitCode = Some(2), stdout = "finding", stderr = "warning"),
        clean.copy(exitCode = Some(2), stdout = "é" * 32769), clean.copy(stderr = "x" * 65537),
        clean.copy(exitCode = Some(1)), clean.copy(exitCode = Some(7)), clean.copy(exitCode = Some(128)))
      When("retained admission checks completion exit UTF-8 byte bounds and NUL before CNCF's pure codec")
      val accepted = invalid.map(receipt => V.successful(Vector(receipt)))
      Then("none becomes a usable structural fact and no new execution is requested")
      accepted shouldBe Vector.fill(invalid.size)(false)
      V.successful(Vector(null.asInstanceOf[V.Receipt])) shouldBe false
      V.successful(Vector(clean.copy(exitCode = Some(2), stdout = "x" * 65536))) shouldBe true
    }

    "retain admitted actual negative captures and leave malformed captures uncertain" in {
      Given("R4: complete raw results from an ordinary failed or actually timed-out selected check")
      def _capture_(text: String): ProcessExecutionCapture = {
        val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
        ProcessExecutionCapture(bytes, bytes.size.toLong, false)
      }
      val failed = ProcessExecutionResult(ProcessExecutionTermination.Exited(7), _capture_("actual failure\n"),
        _capture_("diagnostic\n"), Vector.empty, 1L, "selected-check")
      val unknown = failed.copy(termination = ProcessExecutionTermination.TimedOut)
      When("the existing receipt admission stores only complete bounded strict actual text")
      val failedreceipt = take(V.receiptC("index-local", failed))
      val unknownreceipt = take(V.receiptC("index-local", unknown))
      val malformed = Vector(failed.copy(stdout = failed.stdout.copy(truncated = true)),
        failed.copy(stdout = failed.stdout.copy(byteCount = 0L)),
        failed.copy(stdout = _capture_("x" * 65537)), failed.copy(stdout = _capture_("\u0000")),
        failed.copy(stdout = ProcessExecutionCapture(Vector(0xff.toByte), 1L, false)))
      Then("failure and actual timeout retain their real termination and captures while refused capture supplies no fabricated result for replay")
      failedreceipt shouldBe V.Receipt("index-local", "exited", Some(7), "actual failure\n", "diagnostic\n")
      unknownreceipt shouldBe V.Receipt("index-local", "timed-out", None, "actual failure\n", "diagnostic\n")
      V.successful(Vector(failedreceipt)) shouldBe false
      V.successful(Vector(unknownreceipt)) shouldBe false
      malformed.foreach(result => V.receiptC("index-local", result).isSuccess shouldBe false)
    }
  }

  "Native merge review facts" should {
    "consume only current typed review facts and ignore inert policy or command annotations" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: current Phase5.2; Example: E5; current review input and selected check facts")
      val wire = R.inputCodec.encode(input)
      val check = nested(wire).hcursor.downField("checks").focus.get.asArray.get.head
      val annotated = nested(wire).mapObject(_.add("reviewPolicyIdentity", Json.True).add("approve", Json.True)
        .add("checks", Json.fromValues(input.checks.indices.map { index =>
          nested(wire).hcursor.downField("checks").focus.get.asArray.get(index).mapObject(_.add("command", Json.True))
        })))
      When("current request.v2 decodes unused root and nested annotations")
      val actual = R.inputCodec.decode(replace(wire.mapObject(_.add("authority", Json.True)), annotated))
      Then("the same assigned business input is returned, without policy extraction or command authority")
      actual shouldBe Right(input)

      Given("an unsupported old schema or actual missing/mistyped consumed data")
      val invalid = Vector(wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.repository-native-merge-review-request.v1"))),
        replace(wire, nested(wire).mapObject(_.remove("indexTree"))),
        replace(wire, nested(wire).mapObject(_.add("checks", Json.arr(check.mapObject(_.add("identity", Json.True)))))))
      When("the current-only typed decoder consumes those records")
      val rejected = invalid.map(R.inputCodec.decode)
      Then("required business types and the current schema own refusal, not exact key sets")
      rejected.foreach(_.isLeft shouldBe true)

      Given("an actual semantic review result with an inert command diagnostic")
      val result = R.Result("accept", "Actual reviewed meaning", Vector.empty)
      val resultWire = R.resultCodec.encode(result)
      When("the current result.v1 codec consumes that semantic result")
      val decodedResult = R.resultCodec.decode(replace(resultWire, nested(resultWire).mapObject(_.add("command", Json.True))))
      Then("diagnostics are not a reason to reject the actual result")
      decodedResult shouldBe Right(result)
    }
    "interpret new semantic findings at their actual assigned business boundary" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: current Phase5.2; Example: E6; assigned changed paths and a genuine review disposition")
      val policy = R.Policy("review-installation", 1)
      val finding = R.Finding("finding", Vector("one.scala"), "Repair the assigned source")
      When("a repair result names an assigned path and contains meaningful findings")
      val actual = R.admitC(input, R.Result("repair", "Repair is needed", Vector(finding)), policy)
      Then("the result can select its semantic disposition without recertifying the whole input")
      take(actual) shouldBe ()

      Given("new findings outside the actual assignment or contradictory accept/repair dispositions")
      val invalid = Vector(R.Result("repair", "Repair is needed", Vector(finding.copy(affectedPaths = Vector("outside.scala")))),
        R.Result("accept", "Accepted", Vector(finding)), R.Result("repair", "Repair is needed", Vector.empty))
      When("the owning semantic consumer interprets those actual results")
      val rejected = invalid.map(R.admitC(input, _, policy))
      Then("foreign findings or contradictory result meaning cannot select semantic acceptance")
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }

  "Native Start and downstream records" should {
    "store consumed Start context and actual fetch data without command copies" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E10; original Start and actual fetch facts")
      val context = Fetch.StartContext("original-selection", "original-invocation", "participant", "repository",
        "main", "origin", "main", "endpoint", "FULL_STATE")
      val started = Fetch.Payload("Started", Some(context), "/selected/repository", None, None, None, None)
      val startWire = Fetch.codec(true).encode(started)
      val fetched = Fetch.Payload("Fetched", None, "/selected/repository", Some(before), Some("c" * 40),
        Some(before.copy(mergeBase = Some("a" * 40))), Some("executed"))
      val fetchWire = Fetch.codec(false).encode(fetched)
      When("current Start and fetched output are written and read")
      val decodedStart = Fetch.codec(true).decode(startWire)
      val decodedFetch = Fetch.codec(false).decode(fetchWire)
      Then("only actual context/observations are retained, without commands or policy versions")
      decodedStart shouldBe Right(started)
      decodedFetch shouldBe Right(fetched)
      nested(startWire).asObject.get.keys.toSet should not contain "originalCommand"
      nested(fetchWire).asObject.get.keys.toSet should not contain "originalCommand"
      nested(startWire).hcursor.downField("context").focus.get.asObject.get.keys.toSet should not contain "repositoryPolicyVersion"

      Given("current rows annotated with unused command/authority fields and an invalid consumed participant type")
      val actualContext = nested(startWire).hcursor.downField("context").focus.get
      val annotated = nested(startWire).mapObject(_.add("originalCommand", Json.True)
        .add("context", actualContext.mapObject(_.add("authority", Json.Null))))
      When("the current typed codec reads those records")
      val accepted = Fetch.codec(true).decode(replace(startWire, annotated))
      val malformed = Fetch.codec(true).decode(replace(startWire, nested(startWire).mapObject(_.add("context",
        actualContext.mapObject(_.add("participantIdentity", Json.True))))))
      Then("unused fields are inert, but a wrong consumed type or old representation is unsupported")
      accepted shouldBe Right(started)
      malformed.isLeft shouldBe true
      Vector("v1", "v2").foreach(version => Fetch.codec(true).decode(startWire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.repository-sync-managed-fetch." + version)))).isLeft shouldBe true)
    }
    "consume current progress and ignore unused fields without old-format readers" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E11; selected native progress facts")
      val saved = N.Payload("Preserving", "/selected/repository", before)
      val wire = N.codec.encode(saved)
      When("current v3 progress is written with inert annotations")
      val annotated = replace(wire, nested(wire).mapObject(_.add("originalCommand", Json.True).add("materialization", Json.False)))
      val decoded = N.codec.decode(annotated)
      Then("actual progress is read without retaining those old fields")
      decoded shouldBe Right(saved)
      nested(wire).asObject.get.keys.toSet should not contain "originalCommand"
      saved.productElementNames.toSet should not contain "originalCommand"

      Given("missing consumed progress data and unsupported old schemas")
      When("those records reach the typed current reader")
      val missing = N.codec.decode(replace(wire, nested(wire).mapObject(_.remove("before"))))
      Then("required typed observations fail and no old reader is selected")
      missing.isLeft shouldBe true
      Vector("v1", "v2").foreach(version => N.codec.decode(wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.repository-sync-native-progress." + version)))).isLeft shouldBe true)
    }
  }
}
