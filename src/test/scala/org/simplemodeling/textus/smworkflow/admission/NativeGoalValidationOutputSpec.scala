package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import WorkflowSbtValidation.*
import GoalPhaseNativeValidation as G
import GoalPhaseOrderedProgression as O
import RepositorySyncNativeMergeValidation as R
import SplitPhaseExplicitPartitionFixture.take

/** Public projection of actual retained validation facts, without runtime effects.
  * @since   Oct. 16, 2026
  *  version Oct. 25, 2026
  * @version Oct. 31, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeGoalValidationOutputSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _snapshot = O.Snapshot("a" * 40, "b" * 40)
  private val _classes = Vector("example.FirstSpec", "example.SecondSpec")
  private val _stdout = "actual output\r\n日本語 λ\nsecond line\n"
  private val _stderr = "diagnostic Ω\n二行目\r\n"
  private val _test = G.Check("ordinary", 1, Kind.Test)
  private val _focused = G.Check("focused", 2, Kind.TestOnly)

  private def _capture(text: String): ProcessExecutionCapture = {
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    ProcessExecutionCapture(bytes, bytes.size.toLong, false)
  }
  private def _receipt(check: G.Check, termination: ProcessExecutionTermination,
    selection: Option[SbtTestSelectionResult] = None): R.Receipt = {
    val result = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-" + check.ordinal))),
      if (check.kind == Kind.TestOnly) SbtOperation.TestOnly else SbtOperation.Test,
      ProcessExecutionResult(termination, _capture(_stdout), _capture(_stderr), Vector.empty, 7L, "selected-sbt"), selection)
    take(R.sbtReceiptC(check.identity, check.kind, result))
  }
  private def _payload(selected: Vector[G.Check], receipts: Vector[R.Receipt], outcome: Option[String],
    scope: String = "S1"): G.Payload = G.Payload("8", scope, _snapshot, selected, receipts, outcome)
  private def _retained(payload: G.Payload): G.Payload = G.codec.decode(G.codec.encode(payload)).toOption.get
  private def _wait(payload: G.Payload, condition: String = "phase.Decision"): V.GoalPhaseAdvanceResult =
    NativeWorkflowOperationOutput.goalWait("native-session", "Actual validation remains unfinished.", condition)
      .copy(validation = Some(NativeWorkflowOperationOutput.goalValidation(payload)))
  private def _wire(value: V.GoalPhaseAdvanceResult): V.GoalPhaseAdvanceResult = {
    val record = take(new RecordDecoder().json(take(value.toRecord().toJsonStringC)))
    take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(record))
  }
  private def _legacy(value: Json): Json = value.arrayOrObject(value,
    items => Json.fromValues(items.map(_legacy)),
    fields => Json.obj(fields.toVector.filterNot((key, _) => key == "kind" || key == "sbt")
      .map((key, item) => key -> _legacy(item))*))

  "Failed validation output" should {
    "O1 retain Step scope, ordered selection and exact nonzero Test output in a normal Wait" in {
      Given("a selected Step and its actual nonzero Test receipt with bounded Unicode and multiline captures")
      val receipt = _receipt(_test, ProcessExecutionTermination.Exited(7))
      val payload = _retained(_payload(Vector(_test, _focused), Vector(receipt), Some("failed")))

      When("the public validation projection and generated Record JSON reader carry the actual failed Wait")
      val original = _wait(payload)
      val actual = _wire(original)

      Then("the business facts survive without issuing work, review, Human or authority")
      actual shouldBe original
      actual.sessionId shouldBe "native-session"
      actual.kind shouldBe "Wait"
      actual.condition shouldBe Some("phase.Decision")
      actual.outcome shouldBe None
      val view = actual.validation.get
      view.sourceRevision shouldBe "8"
      view.scopeIdentity shouldBe "S1"
      view.candidate shouldBe V.GoalPhaseNativeSnapshot(_snapshot.head, _snapshot.tree)
      view.selectedChecks.map(c => (c.identity, c.kind)) shouldBe Vector("ordinary" -> "test", "focused" -> "test-only")
      view.outcome shouldBe Some("failed")
      view.checks.map(_.identity) shouldBe Vector("ordinary")
      val check = view.checks.head
      check.termination shouldBe "exited"
      check.exitCode shouldBe Some(7)
      check.stdout shouldBe _stdout
      check.stderr shouldBe _stderr
      check.sbt shouldBe Some(V.WorkflowSbtValidationFacts("test", "failed", None, None))
      actual.submitOperation shouldBe None
      actual.submitSessionId shouldBe None
      actual.resultType shouldBe None
      actual.work shouldBe None
      actual.issuedWorkContext shouldBe None
      actual.human shouldBe None
      actual.authorityContext shouldBe None
      actual.completion shouldBe None
      actual.repositoryConflict shouldBe None
      actual.repositoryReview shouldBe None
      actual.repositoryHuman shouldBe None
      actual.repositoryRepair shouldBe None
    }

    "O2 preserve all eight canonical raw-zero TestOnly rejection reasons without selected-test success" in {
      Given("raw-zero TestOnly receipts with each actual CNCF rejection reason and no verified selection")
      val reasons = SbtTestSelectionFailure.values.toVector
      val payloads = reasons.map(reason => _retained(_payload(Vector(_focused),
        Vector(_receipt(_focused, ProcessExecutionTermination.Exited(0), Some(SbtTestSelectionResult.Rejected(reason)))),
        Some("failed"))))

      When("the persisted negative facts pass through public validation and generated wire decoding")
      val outputs = payloads.map(payload => _wire(_wait(payload)))

      Then("every exact refusal remains unverified despite its zero raw exit and no review is fabricated")
      outputs.map(_.validation.get.checks.head.sbt.get.reason.get) shouldBe
        Vector("MissingSelection", "IncompleteCapture", "InvalidReport", "ZeroTests", "SuiteMismatch", "FailedTests", "ScenarioMismatch", "InventoryMismatch")
      outputs.foreach { output =>
        output.kind shouldBe "Wait"
        output.condition shouldBe Some("phase.Decision")
        output.validation.get.outcome shouldBe Some("failed")
        val check = output.validation.get.checks.head
        check.exitCode shouldBe Some(0)
        check.sbt.get.kind shouldBe "test-only"
        check.sbt.get.outcome shouldBe "unverified-tests"
        check.sbt.get.selection shouldBe None
        check.stdout shouldBe _stdout
        check.stderr shouldBe _stderr
        output.work shouldBe None
        output.repositoryReview shouldBe None
        output.outcome shouldBe None
      }
    }

    "O3 retain typed incomplete TestOnly facts, diagnostic captures and an absent raw exit" in {
      Given("a typed TestOnly result with actual timeout termination and complete bounded diagnostics")
      val receipt = _receipt(_focused, ProcessExecutionTermination.TimedOut)
      val payload = _retained(_payload(Vector(_focused), Vector(receipt), Some("failed")))

      When("public validation projects and serializes that actual failed result")
      val actual = _wire(_wait(payload))

      Then("actual timeout remains timed-out and incomplete, and neither raw exit nor selected success is invented")
      actual.validation.get.outcome shouldBe Some("failed")
      val check = actual.validation.get.checks.head
      check.termination shouldBe "timed-out"
      check.exitCode shouldBe None
      check.stdout shouldBe _stdout
      check.stderr shouldBe _stderr
      check.sbt shouldBe Some(V.WorkflowSbtValidationFacts("test-only", "incomplete", None, None))
      actual.work shouldBe None
      actual.outcome shouldBe None
    }
  }

  "Pending and historical validation output" should {
    "O4 preserve an unresolved intent with either no receipt or its actual completed prefix" in {
      Given("ordered selected Test and TestOnly checks with pending outcome and an optional completed Test prefix")
      val completed = _receipt(_test, ProcessExecutionTermination.Exited(0))
      val payloads = Vector(Vector.empty[R.Receipt], Vector(completed)).map(receipts =>
        _retained(_payload(Vector(_test, _focused), receipts, None, "phase:PHASE-6")))

      When("the partial whole-Phase payloads are projected and decoded through the generated Record reader")
      val views = payloads.map(payload => _wire(_wait(payload, "phase.PhaseValidation")).validation.get)

      Then("outcome remains absent, selection stays ordered and only the actual prefix is returned")
      views.foreach { view =>
        view.scopeIdentity shouldBe "phase:PHASE-6"
        view.sourceRevision shouldBe "8"
        view.candidate shouldBe V.GoalPhaseNativeSnapshot(_snapshot.head, _snapshot.tree)
        view.outcome shouldBe None
        view.selectedChecks.map(c => (c.identity, c.kind)) shouldBe Vector("ordinary" -> "test", "focused" -> "test-only")
      }
      views.map(_.checks.map(_.identity)) shouldBe Vector(Vector.empty, Vector("ordinary"))
      views(1).checks.head.sbt shouldBe Some(V.WorkflowSbtValidationFacts("test", "succeeded", None, None))
      views(1).checks.head.stdout shouldBe _stdout
      views(1).checks.head.stderr shouldBe _stderr
    }

    "O6 load historical absent SBT facts through the current codec and leave public facts absent" in {
      Given("a historical Check without kind and a legacy receipt without SBT or raw process observation")
      val receipt = _receipt(_focused, ProcessExecutionTermination.Exited(0),
        Some(SbtTestSelectionResult.Verified(_classes, 2, 9L))).copy(sbt = None, processObservation = None)
      val historical = _legacy(G.codec.encode(_payload(Vector(_focused), Vector(receipt), Some("passed"))))

      When("the current codec loads the historical shape and public output projects and serializes it")
      val loaded = G.codec.decode(historical).toOption.get
      val actual = _wire(_wait(loaded, "phase.Review")).validation.get

      Then("only historical Test meaning applies and no TestOnly selection, reason or new receipt appears")
      loaded.selected shouldBe Vector(_focused.copy(kind = Kind.Test))
      loaded.receipts shouldBe Vector(receipt)
      actual.selectedChecks.map(_.kind) shouldBe Vector("test")
      actual.checks.size shouldBe 1
      actual.checks.head.sbt shouldBe None
      actual.checks.head.exitCode shouldBe Some(0)
      actual.checks.head.stdout shouldBe _stdout
      actual.checks.head.stderr shouldBe _stderr
      actual.outcome shouldBe Some("passed")
      G.codec.encode(loaded) shouldBe historical
    }
  }

  "Verified TestOnly output" should {
    "O8 refuse oversized or control-character metadata receipt identities and preserve ordinary receipt identity limits" in {
      Given("an explicitly synthetic nonzero result for identity admission only and a real bounded metadata selection")
      val (selected, _) = GoalPhaseNativeMetadataFixture.prepared
      val raw = ProcessExecutionResult(ProcessExecutionTermination.Exited(7), _capture(_stdout), _capture(_stderr),
        Vector.empty, 7L, "synthetic-negative")
      val result = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))),
        SbtOperation.TestOnly, raw)
      val rejectedids = Vector("metadata:" + ("x" * 4096), "metadata:invalid\u0000identity", "metadata:invalid\nidentity",
        "x" * 129, "foreign:identity")

      When("SBT receipt admission checks those identities and generic receipts retain the ordinary host identity contract")
      val rejected = rejectedids.map(identity => R.sbtReceiptC(identity, Kind.TestOnly, result))
      val ordinary = R.receiptC("ordinary", raw)
      val genericmetadata = R.receiptC(selected.head.identity, raw)
      val genericoversized = R.receiptC("x" * 129, raw)

      Then("invalid identities fail without weakening generic receipt limits or inventing selected-test success")
      rejected.foreach(_.isSuccess shouldBe false)
      ordinary.isSuccess shouldBe true
      take(ordinary).exitCode shouldBe Some(7)
      genericmetadata.isSuccess shouldBe false
      genericoversized.isSuccess shouldBe false
    }

    "O7 carry actual selected metadata through validation review and generated Record readers" in {
      Given("a real fixture selection and an explicitly synthetic nonzero diagnostic receipt used only for passive transport")
      val (selected, batches) = GoalPhaseNativeMetadataFixture.prepared
      val receipt = _receipt(selected.head, ProcessExecutionTermination.Exited(7))
      val payload = _retained(_payload(selected, Vector(receipt), Some("failed")).copy(metadata = batches))
      val request = NativeHumanEvidenceFixture.work.copy(review = Some(GoalPhaseNativeReview.Context(
        Vector("one.scala"), "actual selected diff", Vector(receipt), batches)))

      When("public validation and review projections roundtrip through their generated Record readers")
      val validation = _wire(_wait(payload)).validation.get
      val review = NativeWorkflowOperationOutput.goalWork(request)
      val restored = take(summon[ValueReader[V.GoalPhaseWorkView]].readC(
        take(new RecordDecoder().json(take(review.toRecord().toJsonStringC)))))
      val metadata = validation.metadata.map(record => io.circe.parser.parse(take(record.toJsonStringC)).toOption.get)
      val operations = validation.selectedChecks.map(_.operation.map(record =>
        io.circe.parser.parse(take(record.toJsonStringC)).toOption.get))

      Then("canonical Batch context and exact logical references survive with original host ids and raw diagnostics")
      metadata shouldBe batches.map(_.asJson)
      restored shouldBe review
      restored.review.get.metadata.map(record => io.circe.parser.parse(take(record.toJsonStringC)).toOption.get) shouldBe metadata
      validation.selectedChecks.map(_.hostIdentity) shouldBe Vector.fill(2)(Some("selected-metadata"))
      operations shouldBe batches.head.references.map { reference =>
        import GoalPhaseNativeMetadata.given
        Some(reference.asJson)
      }
      validation.checks.head.stdout shouldBe _stdout
      validation.checks.head.stderr shouldBe _stderr
      metadata.head.hcursor.downField("inventory").get[Vector[Json]]("diagnostics") shouldBe Right(Vector.empty)
      validation.outcome shouldBe Some("failed")
      restored.review.get.reviewContext shouldBe None
    }

    "O5 share exact two-suite facts between validation and Goal review through generated Record JSON roundtrips" in {
      Given("verified TestOnly with two completed suites, a positive Long count and an actual Goal review context")
      val tests = 2147483657L
      val receipt = _receipt(_focused, ProcessExecutionTermination.Exited(0),
        Some(SbtTestSelectionResult.Verified(_classes, 2, tests)))
      val payload = _retained(_payload(Vector(_focused), Vector(receipt), Some("passed")))
      val plan = O.Plan("selected-plan", "1", "PHASE-6", "repository",
        Vector(O.Step("S1", "Selected work", Vector("one.scala"), Vector.empty, Vector("focused"))), Vector("focused"))
      val request = GoalPhaseNativeWork.Request("native-session", "9", "ReviewStep", Some("S1"), 1L,
        "Review selected work", plan, _snapshot, Vector.empty, Some(_snapshot), Some("actual-checks"), None,
        Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "actual diff", Vector(receipt))))

      When("validation and the actual Goal review mapping cross generated Record, strict JSON and ValueReader boundaries")
      val validation = _wire(_wait(payload, "phase.Review"))
      val review = NativeWorkflowOperationOutput.goalWork(request)
      val record = take(new RecordDecoder().json(take(review.toRecord().toJsonStringC)))
      val restored = take(summon[ValueReader[V.GoalPhaseWorkView]].readC(record))

      Then("both outputs agree on canonical facts and preserve numeric counts and raw output without acceptance")
      val expected = V.WorkflowSbtValidationFacts("test-only", "succeeded",
        Some(V.WorkflowSbtTestSelection(_classes, 2, tests)), None)
      receipt.sbt shouldBe Some(Facts(Kind.TestOnly, Outcome.Succeeded, Some(Selection(_classes, 2, tests)), None))
      validation.validation.get.outcome shouldBe Some("passed")
      validation.validation.get.checks.head.sbt shouldBe Some(expected)
      restored shouldBe review
      restored.review.get.checks shouldBe validation.validation.get.checks
      restored.review.get.checks.head.exitCode shouldBe Some(0)
      restored.review.get.checks.head.stdout shouldBe _stdout
      restored.review.get.checks.head.stderr shouldBe _stderr
      restored.review.get.reviewContext shouldBe None
      validation.kind shouldBe "Wait"
      validation.work shouldBe None
      validation.completion shouldBe None
      validation.authorityContext shouldBe None
      validation.outcome shouldBe None
    }
  }
}
