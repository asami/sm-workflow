package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import IncrementalSbtDiagnosisRepair as D
import D.given
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as W
import GoalPhaseNativeValidation as N
import WorkflowSbtValidation as S
import RepositorySyncNativeMergeValidation as R

/** Specification-owned typed failures exercise actual semantic admission,
  * native progression, result codecs and information selectors. This focused
  * suite does not claim installed RRC or connected external SBT acceptance.
  * @since   Oct. 25, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class IncrementalSbtDiagnosisRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _failed = O.Snapshot(_base.head, "c" * 40)
  private val _fresh = O.Snapshot(_base.head, "d" * 40)
  private val _plan = O.Plan("diagnostic-plan", "1", "6.6", "repo",
    Vector(O.Step("D3", "Repair type and behavior", Vector("src"), Vector.empty, Vector("compile", "focused"))),
    Vector("compile", "focused"), 10)
  private val _checks = Vector(N.Check("compile", 1, S.Kind.Compile), N.Check("focused", 2, S.Kind.TestOnly))
  private def _key(state: O.State) = state.next.asInstanceOf[O.Semantic].key
  private def _inspection = {
    val initial = take(O.startC(_plan, _base))
    take(take(initial.acceptC(O.SemanticCompleted(_key(initial), "Initial implementation"))).acceptC(O.CandidateObserved(_failed)))
  }
  private def _receipt(id: String, kind: S.Kind, failed: Boolean, text: String) = R.Receipt(id, "exited", Some(if (failed) 1 else 0), text, "",
    Some(S.Facts(kind, if (failed) S.Outcome.Failed else S.Outcome.Succeeded,
      Option.when(!failed && kind == S.Kind.TestOnly)(S.Selection(Vector("example.FocusedBehaviorSpec"), 1, 2L)), None)))
  private def _failure(configuration: String = "Compile") = N.Payload("12", "D3", _failed, _checks,
    Vector(_receipt("compile", S.Kind.Compile, true,
      "[error] -- [E007] Type Mismatch Error: src/Reader.scala:9:2\n[error] Found: String\n[error] Required: Int\n" +
        s"[error] (app / $configuration / compileIncremental) Compilation failed\n")), Some("repair-required"))
  private def _work(failure: N.Payload = _failure()) = W.Request("session", "14", "RepairStep", Some("D3"), 2,
    "Repair type and behavior", _plan, _base, Vector.empty, None, None,
    Some(O.RepairDirective("Correct actual selected failure", Vector("Correct compilation before behavioral execution"))),
    checkFailure = Some(W.CheckFailureContext(Vector("src/Reader.scala"), "Actual-shaped changed source", failure)))
  private def _manifest(failure: N.Payload = _failure()) = D.Manifest(D.schema, "type-correction-1", failure.sourceRevision,
    failure.candidate, "The selected declaration returns String where Int is required; one type correction precedes behavioral checks",
    Vector(D.Locator(failure.receipts.head.identity, "stdout", 1, 3, "Actual retained type and source details")), Vector.empty,
    Vector("src/Reader.scala"), Vector("Return the declared Int while retaining behavior"),
    Vector("The existing Reader declaration and focused fixture must agree"), Vector("compile", "focused"),
    Vector("Compilation alone does not establish behavior"))
  private def _result(manifest: D.Manifest = _manifest()) = W.Result("completed", "Implemented the concrete assigned type correction", Vector.empty,
    diagnosticRepair = Some(manifest))

  "One concrete diagnostic repair within existing work" should {
    "connect batched information and the manifest to normal repair checks without an extra repair assignment" in {
      for (configuration <- Vector("Compile", "Test")) {
        Given("a retained compilation failure and an already assigned ordinary native repair")
        val failure = _failure(configuration)
        val state = take(N.observedStateC(_inspection, failure, "failed-native-check"))
        state.repairs shouldBe 1
        val work = _work(failure)
        val before = W.inputCodec.encode(work)
        val info = take(IncrementalSbtDiagnosisInformation.itemsC(work, Vector(
          IncrementalSbtDiagnosisInformation.Selection("compiler", "failure-context", "Inspect type details", Some("compile"), Some("stdout"), Some(1), None),
          IncrementalSbtDiagnosisInformation.Selection("declaration", "source-file", "Inspect related declaration", None, None, None, Some("src/Reader.scala"))),
          new IncrementalSbtDiagnosisInformation.SourceReader {
            def readC(path: String) = Consequence.success(Some("def read: Int = wrongString"))
          }))
        info.size shouldBe 2
        IncrementalSbtDiagnosisInformation.failedCandidate(work) shouldBe Some(failure.candidate)
        W.inputCodec.encode(work) shouldBe before
        state.repairs shouldBe 1
        When("the provider supplies one concrete cause/evidence/scope/dependency/verification unit and implementation returns it")
        val manifest = _manifest(failure)
        val result = _result(manifest)
        take(W.admitSemanticResultC(work, result))
        val implemented = take(state.acceptC(O.SemanticCompleted(_key(state), result.summary)))
        val inspected = take(implemented.acceptC(O.CandidateObserved(_fresh)))
        val positive = N.Payload("20", "D3", _fresh, _checks,
          _checks.map(c => _receipt(c.identity, c.kind, false, "retained fresh success")), Some("passed"))
        val next = take(N.observedStateC(inspected, positive, "fresh-native-checks"))
        Then("actual-shaped nonempty behavioral facts return to ordinary review/admission without another charge")
        next.stage shouldBe O.Stage.Review
        next.repairs shouldBe 1
        next.ordinaryRepairs shouldBe 1
        positive.receipts.last.sbt.get.selection.get.testsRun shouldBe 2
        failure.receipts.head.exitCode shouldBe Some(1)
      }
    }

    "refuse compile-only proposals, foreign scope, stale candidates and invented log locators" in {
      val work = _work()
      val original = _manifest()
      val invalid = Vector(original.copy(checks = Vector("compile")), original.copy(checks = Vector("compile", "not-selected")),
        original.copy(ownedPaths = Vector("outside/Reader.scala")), original.copy(sourceRevision = "11"),
        original.copy(candidate = _fresh), original.copy(evidence = Vector(D.Locator("compile", "stdout", 1, 999, "Missing lines"))),
        original.copy(evidence = Vector(D.Locator("unselected", "stdout", 1, 1, "Foreign check"))),
        original.copy(evidence = Vector(D.Locator("compile", "stderr", 1, 1, "Empty stream"))))
      invalid.foreach(m => D.admitC(work, _result(m)).isSuccess shouldBe false)
      D.admitC(work.copy(kind = "ImplementStep"), _result()).isSuccess shouldBe false
      D.admitC(work.copy(kind = "ReviewStep"), _result()).isSuccess shouldBe false
      D.admitC(work, _result().copy(outcome = "decision-needed")).isSuccess shouldBe false
    }

    "admit a primary report manifest without absorbing frozen unexecuted dependency checks" in {
      Given("the real typed combined-selection producer and a frozen dependency before execution")
      val fixture = GoalPhaseStepTestReportRepairFixture.frozenDependencyFixture()
      val failure = fixture.validation.payload
      val work = _work(failure).copy(instanceIdentity = fixture.from.identity.value,
        stepIdentity = Some("T4"), selectedPlan = fixture.controller.plan,
        originalBase = fixture.controller.base)
      val manifest = _manifest(failure).copy(identity = "primary-invalid-report-correction",
        evidence = Vector(D.Locator("focused", "stdout", 1, 1, "Retained malformed multi-run report")))
      val before = N.codec.encode(failure)
      val primary = failure.selected.filter(_.dependency.isEmpty)
      val dependency = failure.selected.filter(_.dependency.nonEmpty)
      primary.map(_.identity) shouldBe Vector("compile", "focused")
      dependency.map(_.identity) shouldBe Vector("dependency-compile", "dependency-focused")
      failure.dependency should not be empty
      failure.dependencyExecutions shouldBe empty
      failure.receipts.map(_.identity) shouldBe primary.map(_.identity)
      GoalPhaseStepTestReportRepair.frozenDependencyFailureValid(failure) shouldBe true
      val publicView = NativeWorkflowOperationOutput.goalValidation(failure)
      publicView.selectedChecks.map(_.identity) shouldBe primary.map(_.identity)
      publicView.dependencyValidation should not be empty

      When("the manifest selects only the adopted primary Step compile and behavioral checks")
      D.admitC(work, _result(manifest)).isSuccess shouldBe true
      Then("all original dependency selections, capture and failed receipt remain unchanged")
      N.codec.encode(failure) shouldBe before
      failure.selected shouldBe take(GoalPhaseNativeDependencyValidation.checksC(primary, failure.dependency))
      failure.outcome shouldBe Some("failed")

      And("missing primary compile, dependency smuggling and crossed selection metadata are refused")
      val invalid = Vector(manifest.copy(checks = Vector("focused")),
        manifest.copy(checks = manifest.checks :+ "dependency-compile"),
        manifest.copy(checks = Vector("compile", "dependency-focused")),
        manifest.copy(evidence = Vector(D.Locator("dependency-focused", "stdout", 1, 1, "Unexecuted dependency"))))
      invalid.foreach(m => D.admitC(work, _result(m)).isSuccess shouldBe false)
      val crossed = failure.copy(selected = failure.selected.map(c =>
        if (c.identity == "compile") c.copy(dependency = Some(failure.dependency.get.admission.binding.identity)) else c))
      D.admitC(work.copy(checkFailure = work.checkFailure.map(_.copy(validation = crossed))),
        _result(manifest)).isSuccess shouldBe false
      val foreign = failure.copy(selected = failure.selected.map(c =>
        if (c.identity == "compile") c.copy(identity = "foreign-compile") else c))
      D.admitC(work.copy(checkFailure = work.checkFailure.map(_.copy(validation = foreign))),
        _result(manifest)).isSuccess shouldBe false
    }

    "retain declared Step and Phase identities across real metadata expansion and ordinary checks" in {
      for (phase <- Vector(false, true); ordinaryBehavior <- Vector(false, true)) {
        Given(s"real metadata discovery with ordinary Compile, Phase=$phase and ordinary behavior=$ordinaryBehavior")
        val metadata = GoalPhaseNativeMetadataFixture
        val requested = Vector(N.Check("compile", 1, S.Kind.Compile), metadata.check) ++
          Option.when(ordinaryBehavior)(N.Check("ordinary-behavior", 3, S.Kind.TestOnly)).toVector
        val (selected, batches) = take(GoalPhaseNativeMetadata.prepareC(requested, metadata.loader))
        GoalPhaseNativeMetadata.validateRetainedC(requested, selected, batches).isSuccess shouldBe true
        val declared = requested.map(_.identity)
        GoalPhaseNativeMetadata.declaredIdentities(selected) shouldBe declared
        selected.size shouldBe requested.size + 1 // The real profile expands to two operations.
        val expanded = selected.filter(_.hostIdentity.contains(metadata.check.identity))
        expanded.size shouldBe 2
        val failed = expanded.head
        val failure = N.Payload("12", if (phase) "phase:6.6" else "D3", _failed, selected, Vector(
          _receipt("compile", S.Kind.Compile, false, "Compilation succeeded"),
          _receipt(failed.identity, S.Kind.TestOnly, true, "Retained selected metadata failure\nActual: false\nExpected: true\n")),
          Some("failed"), metadata = batches)
        val plan = _plan.copy(steps = _plan.steps.map(_.copy(checks = declared)), phaseChecks = declared)
        val work = _work(failure).copy(selectedPlan = plan, stepIdentity = Option.unless(phase)("D3"))
        val manifest = _manifest(failure).copy(identity = "expanded-metadata-correction",
          checks = declared, evidence = Vector(D.Locator(failed.identity, "stdout", 1, 3,
            "The actual expanded operation retains the failed behavioral observation")))

        When("the manifest names declared checks while evidence names the actual expanded operation")
        D.admitC(work, _result(manifest)).isSuccess shouldBe true
        Then("the original host mapping and every compile, behavior and evidence guard remain required")
        D.admitC(work, _result(manifest.copy(checks = declared.filterNot(_ == "compile")))).isSuccess shouldBe false
        D.admitC(work, _result(manifest.copy(checks = Vector("compile")))).isSuccess shouldBe false
        D.admitC(work, _result(manifest.copy(checks = Vector("compile", failed.identity)))).isSuccess shouldBe false
        val crossed = failure.copy(selected = selected.map(c =>
          if (c.identity == failed.identity) c.copy(hostIdentity = Some("foreign-metadata-host")) else c))
        D.admitC(work.copy(checkFailure = work.checkFailure.map(_.copy(validation = crossed))),
          _result(manifest)).isSuccess shouldBe false
        D.admitC(work, _result(manifest.copy(evidence = Vector(D.Locator(metadata.check.identity,
          "stdout", 1, 1, "The declared host is not an executed receipt identity"))))).isSuccess shouldBe false
        failure.selected shouldBe selected
        failure.metadata shouldBe batches
        failure.outcome shouldBe Some("failed")
      }
    }

    "retain earlier hypotheses, failures and consumed counts when later behavioral failure requires revision" in {
      val first = take(N.observedStateC(_inspection, _failure(), "compile-failed"))
      val corrected = take(first.acceptC(O.SemanticCompleted(_key(first), "Type corrected")))
      val inspected = take(corrected.acceptC(O.CandidateObserved(_fresh)))
      val testfailure = N.Payload("20", "D3", _fresh, _checks, Vector(
        _receipt("compile", S.Kind.Compile, false, "compiled"),
        _receipt("focused", S.Kind.TestOnly, true, "[info] - retains behavior *** FAILED ***\n[info] Expected: 42\n[info] Actual: 41\n")), Some("repair-required"))
      val second = take(N.observedStateC(inspected, testfailure, "behavior-failed"))
      second.repairs shouldBe 2
      second.ordinaryRepairs shouldBe 2
      val history = D.append(Vector.empty, _work(), _result(), ContextReference("accepted-type-repair", "1"))
      val revisedwork = _work(testfailure).copy(generation = 3, diagnosticHistory = D.selected(history, Some("D3")))
      val revised = _manifest(testfailure).copy(identity = "behavior-correction-2",
        causeHypothesis = "Compilation succeeded; the remaining boundary is an off-by-one result",
        evidence = Vector(D.Locator("focused", "stdout", 1, 3, "Actual-shaped failed assertion")),
        directions = Vector("Correct the boundary result without reverting the type correction"))
      D.admitC(revisedwork, _result(revised)).isSuccess shouldBe true
      val all = D.append(history, revisedwork, _result(revised), ContextReference("accepted-behavior-repair", "1"))
      all.head shouldBe history.head
      all.map(_.manifest.identity) shouldBe Vector("type-correction-1", "behavior-correction-2")
      all.map(_.manifest.candidate) shouldBe Vector(_failed, _fresh)
      NativeWorkflowOperationOutput.goalWork(revisedwork).diagnosticHistory.size shouldBe 1
      D.selected(all, Some("other-step")) shouldBe None
      _failure().receipts.head.exitCode shouldBe Some(1)
    }

    "preserve legacy wire shapes and version new manifest/history records without silently downgrading them" in {
      val legacy = W.Result("completed", "Earlier accepted implementation", Vector.empty)
      val oldwire = W.resultCodec.encode(legacy)
      oldwire.hcursor.get[String]("schemaVersion").toOption.get should endWith(".v1")
      oldwire.hcursor.downField("payload").downField("diagnosticRepair").focus shouldBe None
      W.resultCodec.decode(oldwire) shouldBe Right(legacy)
      val result = _result()
      val wire = W.resultCodec.encode(result)
      wire.hcursor.get[String]("schemaVersion").toOption.get should endWith(".v3")
      W.resultCodec.decode(wire) shouldBe Right(result)
      W.resultCodec.decode(wire.deepMerge(Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-work-result.v1")))).isLeft shouldBe true
      val history = D.append(Vector.empty, _work(), result, ContextReference("accepted-repair", "1"))
      val request = _work().copy(diagnosticHistory = Some(history))
      val requestwire = W.inputCodec.encode(request)
      requestwire.hcursor.get[String]("schemaVersion").toOption.get should endWith(".v4")
      W.inputCodec.decode(requestwire) shouldBe Right(request)
      W.inputCodec.decode(W.inputCodec.encode(_work())) shouldBe Right(_work())
      W.inputCodec.decode(requestwire.deepMerge(Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-work-request.v2")))).isLeft shouldBe true
      val public = GoalPhaseNativeMetadata.record(_manifest().asJson)
      take(D.fromPublicC(Some(public))) shouldBe Some(_manifest())
      D.fromPublicC(Some(GoalPhaseNativeMetadata.record(_manifest().asJson.deepMerge(Json.obj("unowned" -> Json.fromBoolean(true)))))).isSuccess shouldBe false
      D.validatePublicResultJsonC(Json.obj("diagnosticRepair" -> Json.Null)).isSuccess shouldBe false
      D.validatePublicResultJsonC(Json.obj("diagnosticRepair" -> Json.obj())).isSuccess shouldBe false
      D.validatePublicResultJsonC(Json.obj("diagnosticRepair" -> _manifest().asJson)).isSuccess shouldBe true
      D.validatePublicResultJsonC(Json.obj("outcome" -> Json.fromString("completed"))).isSuccess shouldBe true
    }
  }
}
