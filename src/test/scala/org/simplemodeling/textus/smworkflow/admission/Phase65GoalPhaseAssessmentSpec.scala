package org.simplemodeling.textus.smworkflow.admission

import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as W
import GoalPhaseNativeValidation as N
import GoalPhaseStepAssessment as A
import RepositorySyncNativeMergeValidation as R
import WorkflowSbtValidation as T
import DevelopmentJournalAssessment as J
import SplitPhaseExplicitPartitionFixture.take

/** Pure typed-boundary evidence; actual CLI/SBT/Git evidence is a separate suite.
  * @since   Oct. 23, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class Phase65GoalPhaseAssessmentSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val base = O.Snapshot("a" * 40, "b" * 40)
  private val candidate = base.copy(tree = "c" * 40)
  private val policy = A.Policy("local-code", "code", "conditional", "off", "off", Vector.empty)
  private val checks = Vector(N.Check("compile", 1, T.Kind.Compile), N.Check("focused", 2, T.Kind.TestOnly))
  private val plan = O.Plan("assessment-plan", "1", "6.5", "specification-owned", Vector(
    O.Step("S1", "Complete the code boundary", Vector("src/main/scala/One.scala"), Vector.empty,
      checks.map(_.identity), actionPolicy = Some(policy)),
    O.Step("S2", "Complete planned integration", Vector("src/main/scala/Two.scala"), Vector("S1"),
      checks.map(_.identity))), Vector("focused"))
  private def validation(p: O.Plan = plan): O.State = {
    val initial = take(O.startC(p, base))
    val ready = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "implemented")))
    take(ready.acceptC(O.CandidateObserved(candidate)))
  }
  private def receipt(c: N.Check) = R.Receipt(c.identity, "exited", Some(0), "specification-owned native-shaped output", "",
    Some(T.Facts(c.kind, T.Outcome.Succeeded,
      Option.when(c.kind == T.Kind.TestOnly)(T.Selection(Vector("example.BehaviorSpec"), 1, 3)), None)))
  private def payload = N.Payload("8", "S1", candidate, checks, checks.map(receipt), Some("passed"))
  private def assessment(state: O.State = validation(), p: N.Payload = payload): A.Result = take(A.assessC(state, p, "native-checks"))
  private def assessed(state: O.State = validation(), p: N.Payload = payload): O.State = {
    val a = assessment(state, p)
    take(N.observedStateC(state, p.copy(assessment = Some(a)), "native-checks"))
  }
  private def withPolicy(p: A.Policy) = plan.copy(steps = plan.steps.updated(0, plan.steps.head.copy(actionPolicy = Some(p))))
  private def _metadata_plan(selectedpolicy: A.Policy): O.Plan = {
    val selected = withPolicy(selectedpolicy)
    selected.copy(steps = selected.steps.updated(0, selected.steps.head.copy(
      checks = Vector(checks.head.identity, GoalPhaseNativeMetadataFixture.check.identity))))
  }
  private def _metadata_payload(multiple: Boolean): N.Payload = {
    val fixture = GoalPhaseNativeMetadataFixture
    val profile = fixture.profile
    val selectedprofile = if (multiple) profile else profile.copy(supplemental = Vector.empty)
    val (selected, batches) = take(GoalPhaseNativeMetadata.prepareC(Vector(checks.head,
      fixture.check.copy(metadata = Some(selectedprofile))), fixture.loader))
    payload.copy(selected = selected, receipts = selected.map(receipt), metadata = batches)
  }
  private def _triage_request(state: O.State, saved: N.Payload): GoalPhaseNativeWork.Request =
    GoalPhaseNativeWork.Request("metadata-session", "20", "TriageStep", Some("S1"), state.generation,
      "Settle the selected metadata uncertainty", state.plan, base, state.completed, state.candidate,
      state.checksReceipt, None, triage = Some(GoalPhaseStepTriage.Context(Vector("src/main/scala/One.scala"),
        "Actual-shaped selected candidate diff", saved.receipts, saved.assessment.get)))
  private def _linked_payload(state: O.State): N.Payload = {
    val fixture = DependencyValidationSpecificationFixture
    val binding = fixture.binding.copy(step = "S1", checks = Vector(fixture.check.copy(ordinal = 3)))
    val admission = fixture.admission.copy(binding = binding, approvals = fixture.approvals.map(_.copy(step = "S1")))
    val capture = fixture.capture.copy(plan = ContextReference(state.plan.identity, state.plan.revision),
      step = "S1", primaryBase = base, admission = admission)
    val selected = take(GoalPhaseNativeDependencyValidation.checksC(checks, Some(capture)))
    payload.copy(selected = selected, receipts = selected.map(receipt), dependency = Some(capture),
      dependencyExecutions = Vector(GoalPhaseDependencyValidation.Execution(binding.checks.head.identity, 0,
        binding.dependency.workspace, binding.checks.head.kind.wire)))
  }
  private def _release_target(reviewless: Boolean): GoalPhaseReleaseRepairContinuation.Target = {
    val boundary = GoalPhaseAcceptanceBoundary
    val selectedplan = take(boundary.captureC(None, plan.copy(steps = Vector(plan.steps.head.copy(
      actionPolicy = Option.when(reviewless)(policy))), repairLimit = 3,
      acceptance = Some(boundary.Adoption("Retain the selected input contract", Vector(
        boundary.Condition("release-contract", "phase", true, "The released contract describes the accepted input")))))))
    val validated = validation(selectedplan)
    val admitted = if (reviewless) assessed(validated) else {
      val checked = take(validated.acceptC(O.ChecksObserved(candidate, checks.map(_.identity), "step-checks", true)))
      take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key, candidate,
        "step-checks", "historical-step-review", O.ReviewOutcome.Accept)))
    }
    val intent = take(admitted.acceptC(O.ClosureIntentSaved(candidate, "step-commit-intent")))
    val closed = take(intent.acceptC(O.StepCommitObserved("step-commit-intent", O.Commit("d" * 40, base.head, candidate.tree))))
    val inspected = take(closed.acceptC(O.CandidateObserved(closed.tipSnapshot)))
    val phase = take(inspected.acceptC(O.ChecksObserved(closed.tipSnapshot, selectedplan.phaseChecks, "phase-checks", true)))
    val reference = ContextReference("legacy-integrated-review", "1")
    val detail = "The released contract must describe the accepted input"
    val result = GoalPhaseNativeWork.Result("repair", "Original integrated review", Vector(detail),
      acceptance = Some(boundary.Report(Vector(boundary.Observation("release-contract", "unsatisfied", detail)), Vector.empty)))
    val issues = GoalPhaseFixIssues.State(Vector(GoalPhaseFixIssues.Issue("issue-1", selectedplan.repository,
      "phase", "REVIEW", None, detail, reference.identity, "OPEN", None)), Vector.empty, None)
    val request = GoalPhaseNativeWork.Request("release-session", "18", "ReviewPhase", None, phase.generation,
      "Review the completed selected Phase", selectedplan, base, phase.completed, phase.candidate, phase.checksReceipt, None,
      review = Some(GoalPhaseNativeReview.Context(Vector("src/main/scala/One.scala"), "Actual integrated diff",
        Vector(receipt(checks.last)))), issues = Some(issues))
    val host = GoalPhaseReleaseRepairContinuation.Host("/specification/repository", "main", selectedplan.steps.head.ownedPaths,
      "phase-validation", 1, Vector(checks.last.copy(ordinal = 1)), "phase-closure", 1,
      Vector(GoalPhaseReleaseRepairContinuation.Program("S1", 1), GoalPhaseReleaseRepairContinuation.Program("phase-release", 2)))
    GoalPhaseReleaseRepairContinuation.Target("release-session", "legacy-question", "20", "21", "original-assignment", "18",
      reference, GoalPhaseNativeWork.inputCodec.encode(request), result, selectedplan, base, phase.completed, phase.generation,
      phase.candidate.get, phase.checksReceipt.get, GoalPhaseRepairProgress.snapshot(phase), issues,
      Vector(GoalPhaseReleaseRepairContinuation.Finding(1, "release-contract", detail, "issue-1")), host)
  }
  private def observation(timing: String, later: Option[String]) = A.Observation("meaning-1", ContextReference("source", "4"),
    "Does the later integration preserve this meaning?", timing, "The current Step's boundary is independently usable", later)
  private def closingFrontier = {
    val initial = take(InstanceRecord(InstanceIdentity("assessment-native"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("assessment-correlation"),
      CausationReference("assessment-start"), None, None, None, Vector.empty).validateC)
    take(initial.appendC(initial.revision, GoalPhaseNativeRun.history(initial, "phase.Closing", "assessment")))
  }
  private def preservesClosing(before: O.State, after: O.State): Unit = {
    after.stage shouldBe O.Stage.Closing
    after.candidate shouldBe before.candidate
    after.checksReceipt shouldBe before.checksReceipt
    after.stepAdmission shouldBe before.stepAdmission
    after.reviewEvidence shouldBe None
    after.generation shouldBe before.generation
    after.repairs shouldBe before.repairs
    after.ordinaryRepairs shouldBe before.ordinaryRepairs
    val input = take(GoalPhaseNativeClosure.inputC(closingFrontier, after))
    input.admission shouldBe before.stepAdmission
    input.reviewEvidence shouldBe None
  }

  "P65-Q1 deterministic Step assessment" should {
    "admit one or multiple real selected metadata operations and complete conditional CLEAN triage with every physical receipt retained" in {
      Given("Compile and a real producer-selected metadata host with one or two operations and either formal or conditional semantic policy")
      val policies = Vector(policy, policy.copy(semanticTriage = "conditional",
        observations = Vector(observation("immediate", None))))
      for (multiple <- Vector(false, true); selectedpolicy <- policies) {
        val state = validation(_metadata_plan(selectedpolicy))
        val original = _metadata_payload(multiple)
        When("assessment and the native ordered continuation consume the complete actual-shaped successful runtime batch")
        val result = take(A.assessC(state, original, "native-metadata-checks"))
        val saved = original.copy(assessment = Some(result))
        val continued = take(N.observedStateC(state, saved, "native-metadata-checks"))
        val restored = N.codec.decode(N.codec.encode(saved))
        val publicview = NativeWorkflowOperationOutput.goalValidation(saved)
        Then("logical Plan names remain the assessment IDs while all selected runtime checks receipts test facts and metadata batches survive")
        result.checks shouldBe state.plan.steps.head.checks
        result.validationReference shouldBe "native-metadata-checks"
        result.branch shouldBe (if (selectedpolicy.observations.isEmpty) "admit" else "triage")
        saved.selected should have size (if (multiple) 3 else 2)
        saved.receipts.map(_.identity) shouldBe saved.selected.map(_.identity)
        saved.receipts.tail.foreach(_.sbt.get.selection.get.testsRun should be > 0L)
        saved.metadata should have size 1
        saved.metadata.head.references should have size (if (multiple) 2 else 1)
        restored shouldBe Right(saved)
        restored.toOption.get.receipts shouldBe original.receipts
        restored.toOption.get.metadata shouldBe original.metadata
        publicview.selectedChecks.map(_.identity) shouldBe original.selected.map(_.identity)
        publicview.checks.map(_.identity) shouldBe original.receipts.map(_.identity)
        val publicbatch = io.circe.parser.parse(take(publicview.metadata.head.toJsonStringC))
          .fold(error => fail(error.message), identity)
        import GoalPhaseNativeMetadata.given
        GoalPhaseNativeMetadata.normalizePublicJson(publicbatch).toOption.get.as[GoalPhaseNativeMetadata.Batch] shouldBe
          Right(original.metadata.head)
        if (result.branch == "triage") {
          Given("the exact issued-shaped triage context from the same successful native validation")
          val request = _triage_request(continued, saved)
          val clean = GoalPhaseNativeWork.Result("clean", "The selected metadata guarantee is retained", Vector.empty)
          When("native and public triage transport roundtrip the raw context and CLEAN is admitted at its assigned frontier")
          val decoded = GoalPhaseNativeWork.inputCodec.decode(GoalPhaseNativeWork.inputCodec.encode(request))
          val publictriage = NativeWorkflowOperationOutput.goalWork(request).triage.get
          val publicjson = io.circe.parser.parse(take(publictriage.toJsonStringC)).fold(error => fail(error.message), identity)
          val context = GoalPhaseNativeMetadata.normalizePublicJson(publicjson).toOption.get.as[GoalPhaseStepTriage.Context]
          val closed = take(GoalPhaseStepTriage.completedC(request, clean, ContextReference("metadata-triage", "1"), continued))
          Then("both transports preserve every physical primary receipt and CLEAN produces formal Closing admission without replay")
          decoded shouldBe Right(request)
          context shouldBe Right(request.triage.get)
          context.toOption.get.checks shouldBe saved.receipts
          GoalPhaseStepTriage.admitC(decoded.toOption.get, clean).isSuccess shouldBe true
          GoalPhaseStepTriage.admitC(request.copy(triage = context.toOption), clean).isSuccess shouldBe true
          closed.stage shouldBe O.Stage.Closing
          closed.stepAdmission.get.assessment.checks shouldBe result.checks
          closed.stepAdmission.get.assessment.triage shouldBe "clean"
          closed.reviewEvidence shouldBe None
          closed.checksReceipt shouldBe continued.checksReceipt
          closed.generation shouldBe continued.generation
          closed.repairs shouldBe continued.repairs
        } else {
          continued.stage shouldBe O.Stage.Closing
          continued.stepAdmission shouldBe Some(A.admission(result))
        }
      }
    }

    "refuse formal admission and conditional triage when any selected metadata receipt fails or is missing" in {
      Given("real one-operation and multiple-operation metadata batches with otherwise successful Compile and selected semantic policies")
      val policies = Vector(policy, policy.copy(semanticTriage = "conditional",
        observations = Vector(observation("immediate", None))))
      for (multiple <- Vector(false, true); selectedpolicy <- policies) {
        val state = validation(_metadata_plan(selectedpolicy))
        val original = _metadata_payload(multiple)
        val valid = take(A.assessC(state, original, "native-metadata-checks"))
        val incomplete = original.receipts.indices.drop(1).flatMap { index =>
          val failed = original.receipts(index).copy(exitCode = Some(1),
            sbt = Some(original.receipts(index).sbt.get.copy(outcome = T.Outcome.Failed)))
          Vector(original.copy(receipts = original.receipts.patch(index, Vector.empty, 1)),
            original.copy(receipts = original.receipts.updated(index, failed)))
        }
        val alternatives = incomplete ++ Vector(original.copy(receipts = original.receipts.reverse),
          original.copy(receipts = original.receipts.take(1) ++ original.receipts.drop(1).reverse),
          original.copy(receipts = original.receipts.updated(1, original.receipts(1).copy(identity = "foreign-host")))).filterNot(_ == original)
        When("a purported passed batch omits or fails each metadata operation in turn")
        val results = alternatives.map(A.assessC(state, _, "native-metadata-checks"))
        Then("neither semantic branch can borrow the remaining success or a previously valid assessment")
        results.foreach(_.isSuccess shouldBe false)
        alternatives.foreach(value => N.observedStateC(state, value.copy(assessment = Some(valid)),
          "native-metadata-checks").isSuccess shouldBe false)
        original.metadata should not be empty
        original.receipts.map(_.identity) shouldBe original.selected.map(_.identity)
      }
    }

    "recognize only canonical bounded metadata identities in triage without collapsing or replacing raw receipts" in {
      Given("an issued-shaped conditional triage context with a real multiple-operation metadata host")
      val selectedpolicy = policy.copy(semanticTriage = "conditional", observations = Vector(observation("immediate", None)))
      val state = validation(_metadata_plan(selectedpolicy))
      val original = _metadata_payload(true)
      val result = take(A.assessC(state, original, "native-metadata-checks"))
      val saved = original.copy(assessment = Some(result))
      val continued = take(N.observedStateC(state, saved, "native-metadata-checks"))
      val request = _triage_request(continued, saved)
      val clean = GoalPhaseNativeWork.Result("clean", "The captured guarantee is retained", Vector.empty)
      val metadata = GoalPhaseNativeMetadata
      val host = GoalPhaseNativeMetadataFixture.check.identity
      val reference = saved.selected(1).operation.get
      val runtime = saved.receipts(1).identity
      val tail = runtime.substring(("metadata:" + host.length + ":").length)
      val overhead = metadata.logicalIdentity(host, reference.copy(operation = "x" * 1000)).length - 1000
      val boundary = metadata.logicalIdentity(host, reference.copy(operation = "x" * (4096 - overhead)))
      val unicode = metadata.logicalIdentity(host, reference.copy(operation = "𝛼:boundary"))
      val positive = Vector(runtime, runtime + ":invocation:1", runtime + ":invocation:16", boundary, unicode)
      When("the structural triage admission recognizes canonical four-segment encodings and the existing invocation bounds")
      val accepted = positive.map { identity =>
        val context = request.triage.get.copy(checks = Vector(saved.receipts.head, saved.receipts(1).copy(identity = identity)))
        GoalPhaseStepTriage.admitC(request.copy(triage = Some(context)), clean)
      }
      Then("String.length segment units and the exact 4096-byte boundary are accepted without claiming native operation selection")
      accepted.foreach(_.isSuccess shouldBe true)
      boundary.length shouldBe 4096
      request.triage.get.checks shouldBe saved.receipts

      Given("malformed lengths suffixes foreign hosts duplicate runtime IDs and interleaved logical groups")
      val malformed = Vector("metadata:0:" + host + tail, "metadata:0" + host.length + ":" + host + tail,
        "metadata:-1:" + tail, "metadata:2147483648:" + tail, "metadata:4096:" + tail,
        "metadata:" + host.length + ":" + host, runtime + "junk", runtime + ":invocation:0",
        runtime + ":invocation:01", runtime + ":invocation:17", runtime + ":invocation:1:extra",
        boundary + "x", metadata.logicalIdentity("foreign-host", reference),
        metadata.logicalIdentity(host, reference.copy(operation = "\u0000")))
      val malformedreceipts = malformed.map(identity => Vector(saved.receipts.head, saved.receipts(1).copy(identity = identity))) ++
        Vector(saved.receipts.reverse, saved.receipts :+ saved.receipts(1),
          Vector(saved.receipts(1), saved.receipts.head, saved.receipts(2)),
          Vector(saved.receipts.head, saved.receipts(1), saved.receipts.head.copy(
            identity = metadata.logicalIdentity(checks.head.identity, reference)), saved.receipts(2)))
      When("only the raw receipt identity vectors are replaced in the otherwise valid issued context")
      val rejected = malformedreceipts.map(receipts => GoalPhaseStepTriage.admitC(
        request.copy(triage = Some(request.triage.get.copy(checks = receipts))), clean))
      Then("every malformed foreign reordered duplicate or repeated logical group is refused")
      rejected.zipWithIndex.foreach { (actual, index) => withClue("runtime identity alternative " + index + ": ") {
        actual.isSuccess shouldBe false
      }}
    }

    "admit or triage only the primary projection after the entire linked batch succeeds" in {
      Given("a code Step with selected primary checks and an admitted dependency Compile in the same saved batch")
      val policies = Vector(policy, policy.copy(semanticTriage = "conditional",
        observations = Vector(observation("immediate", None))))
      policies.foreach { selectedpolicy =>
        val state = validation(withPolicy(selectedpolicy))
        val original = _linked_payload(state)
        When("the native assessment and ordered continuation consume every successful primary and dependency receipt")
        val result = take(A.assessC(state, original, "native-linked-checks"))
        val saved = original.copy(assessment = Some(result))
        val continued = take(N.observedStateC(state, saved, "native-linked-checks"))
        val publicview = NativeWorkflowOperationOutput.goalValidation(saved)
        val dependencyjson = io.circe.parser.parse(take(publicview.dependencyValidation.get.toJsonStringC))
          .fold(error => fail(error.message), identity)
        val dependencyview = GoalPhaseNativeMetadata.normalizePublicJson(dependencyjson).toOption.get
        Then("admission or triage retains the full saved facts while the primary and dedicated dependency views stay distinct")
        result.checks shouldBe checks.map(_.identity)
        result.validationReference shouldBe "native-linked-checks"
        result.branch shouldBe (if (selectedpolicy.observations.isEmpty) "admit" else "triage")
        continued.stage shouldBe (if (selectedpolicy.observations.isEmpty) O.Stage.Closing else O.Stage.Triage)
        publicview.selectedChecks.map(_.identity) shouldBe checks.map(_.identity)
        publicview.checks.map(_.identity) shouldBe checks.map(_.identity)
        dependencyview.hcursor.get[Vector[io.circe.Json]]("checks").toOption.get.map(
          _.hcursor.get[String]("identity").toOption.get) shouldBe original.selected.filter(_.dependency.nonEmpty).map(_.identity)
        dependencyview.hcursor.get[Vector[io.circe.Json]]("executions").toOption.get should have size 1
        publicview.assessment should not be empty
        saved.selected shouldBe original.selected
        saved.receipts shouldBe original.receipts
        saved.dependency shouldBe original.dependency
        saved.dependencyExecutions shouldBe original.dependencyExecutions
        N.codec.decode(N.codec.encode(saved)) shouldBe Right(saved)
      }
    }

    "refuse assessment and triage when a linked dependency fails or has no receipt" in {
      Given("the same complete primary success with a required admitted dependency and either selected semantic policy")
      val policies = Vector(policy, policy.copy(semanticTriage = "conditional",
        observations = Vector(observation("immediate", None))))
      policies.foreach { selectedpolicy =>
        val state = validation(withPolicy(selectedpolicy))
        val original = _linked_payload(state)
        val failed = original.receipts.last.copy(exitCode = Some(1),
          sbt = Some(original.receipts.last.sbt.get.copy(outcome = T.Outcome.Failed)))
        val alternatives = Vector(original.copy(receipts = original.receipts.dropRight(1)),
          original.copy(receipts = original.receipts.updated(original.receipts.size - 1, failed)))
        When("the assessment owner sees missing or failed dependency evidence despite a claimed passed outcome")
        val results = alternatives.map(A.assessC(state, _, "native-linked-checks"))
        Then("neither semantic route can borrow primary success and all original dependency facts remain unchanged")
        results.foreach(_.isSuccess shouldBe false)
        alternatives.foreach(value => N.observedStateC(state, value, "native-linked-checks").isSuccess shouldBe false)
        original.receipts.size shouldBe original.selected.size
        original.dependency should not be empty
        original.dependencyExecutions should have size 1
      }
    }

    "retain dependency validations and a triage judgment together through native and public review views" in {
      Given("a complete linked validation and an attributed suspicious triage judgment for the same primary candidate")
      val state = validation()
      val linked = _linked_payload(state)
      val judgment = GoalPhaseStepTriage.Judgment(ContextReference("linked-triage", "1"), "suspicious",
        "Review the selected linked guarantee", Vector("The public boundary needs detailed judgment"))
      val context = GoalPhaseNativeReview.Context(Vector("src/main/scala/One.scala"), "actual-shaped primary diff",
        linked.receipts.filter(r => checks.exists(_.identity == r.identity)),
        dependencyValidations = Vector(linked), triageJudgment = Some(judgment))
      val request = GoalPhaseNativeWork.Request("linked-session", "20", "ReviewStep", Some("S1"), state.generation,
        "Review the exact primary and linked guarantees", state.plan, base, Vector.empty, Some(candidate),
        Some("native-linked-checks"), None, review = Some(context))
      When("the native work codec and declared public review projection consume both independent additions")
      val restored = GoalPhaseNativeWork.inputCodec.decode(GoalPhaseNativeWork.inputCodec.encode(request))
      val publicview = NativeWorkflowOperationOutput.goalWork(request).review.get
      Then("neither addition occupies the other's optional slot and historical absence stays absent")
      restored shouldBe Right(request)
      publicview.checks.map(_.identity) shouldBe checks.map(_.identity)
      publicview.dependencyValidations should have size 1
      publicview.triageJudgment should not be empty
      val old = request.copy(review = Some(context.copy(dependencyValidations = Vector.empty, triageJudgment = None)))
      GoalPhaseNativeWork.inputCodec.decode(GoalPhaseNativeWork.inputCodec.encode(old)) shouldBe Right(old)
      val oldview = NativeWorkflowOperationOutput.goalWork(old).review.get
      oldview.dependencyValidations shouldBe empty
      oldview.triageJudgment shouldBe None
    }

    "admit actual-shaped code checks without a triage review or waiver result" in {
      Given("a selected conditional code policy and successful Compile plus nonempty focused behavioral facts")
      val state = validation()
      When("the native owner recomputes the exact assessment and continues its ordered controller")
      val actual = assessed(state)
      Then("Closing has a formal admission and no independent-review evidence or semantic request")
      actual.stage shouldBe O.Stage.Closing
      actual.reviewEvidence shouldBe None
      actual.stepAdmission shouldBe Some(A.admission(assessment()))
      actual.next.isInstanceOf[O.Semantic] shouldBe false
      actual.repairs shouldBe state.repairs
      actual.ordinaryRepairs shouldBe state.ordinaryRepairs
      val a = actual.stepAdmission.get.assessment
      a.triage shouldBe "not-selected"
      a.review shouldBe "not-executed"
      a.checks shouldBe checks.map(_.identity)
    }

    "retain the formal admission through commit and issue only the next implementation" in {
      Given("the formally admitted first Step at the actual-shaped Closing frontier")
      val admitted = assessed()
      val pending = take(admitted.acceptC(O.ClosureIntentSaved(candidate, "commit-intent")))
      When("the native owner records its exact single-parent commit")
      val closed = take(pending.acceptC(O.StepCommitObserved("commit-intent", O.Commit("d" * 40, base.head, candidate.tree))))
      Then("the completed prefix carries admission rather than a fake PASS review and resets only the next Step window")
      closed.completed.head.review shouldBe None
      closed.completed.head.admission shouldBe admitted.stepAdmission
      O.validStepAcceptance(closed.completed.head) shouldBe true
      closed.stepAdmission shouldBe None
      closed.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.ImplementStep
      closed.next.asInstanceOf[O.Semantic].key.step shouldBe Some("S2")
    }

    "preserve exact admission and native closure through a compatible later Step document update" in {
      Given("a formally admitted Closing Step and an unprotected later Step purpose change")
      val admitted = assessed()
      val refreshed = admitted.plan.copy(steps = admitted.plan.steps.updated(1,
        admitted.plan.steps(1).copy(purpose = "Complete the clarified planned integration")))
      When("document refresh retains the current candidate and checks with either issued flag")
      val results = Vector(false, true).map(issued => take(admitted.refreshedC(refreshed, issued)))
      Then("both continuations retain the exact admission and remain eligible for native closure without a fake review")
      results.foreach {
        case Right(state) =>
          state.plan shouldBe refreshed
          preservesClosing(admitted, state)
        case Left(conflicts) => fail("Compatible later Step update conflicted: " + conflicts.mkString(", "))
      }
    }

    "preserve exact admission through document conflict and admitted resumption" in {
      Given("a formally admitted Closing frontier with no independent review")
      val admitted = assessed()
      When("the document owner records a conflict and the specification-owned Human-shaped resumption is admitted")
      val decision = take(admitted.acceptC(O.DocumentConflictObserved(Vector("issued-step"))))
      val resumed = take(decision.acceptC(O.DecisionResumptionAdmitted("specification-owned-document-direction")))
      Then("the conflict and resumed frontier preserve candidate checks admission and repair history")
      decision.stage shouldBe O.Stage.Decision
      decision.stepAdmission shouldBe admitted.stepAdmission
      decision.candidate shouldBe admitted.candidate
      decision.checksReceipt shouldBe admitted.checksReceipt
      preservesClosing(admitted, resumed)
    }

    "refuse missing partial failed uncertain mismatched and zero-test evidence" in {
      Given("the complete selected policy with corrupt alternatives at its validation boundary")
      val focused = payload.receipts.last
      val zero = focused.copy(sbt = Some(T.Facts(T.Kind.TestOnly, T.Outcome.Succeeded,
        Some(T.Selection(Vector("example.BehaviorSpec"), 1, 0)), None)))
      val alternatives = Vector(payload.copy(outcome = None), payload.copy(outcome = Some("failed")),
        payload.copy(receipts = payload.receipts.take(1)), payload.copy(receipts = Vector.empty),
        payload.copy(receipts = payload.receipts.reverse), payload.copy(candidate = candidate.copy(tree = "e" * 40)),
        payload.copy(receipts = payload.receipts.updated(1, zero)),
        payload.copy(receipts = payload.receipts.updated(1, focused.copy(termination = "timed-out", exitCode = None))),
        payload.copy(receipts = payload.receipts.updated(1, focused.copy(exitCode = Some(1)))),
        payload.copy(selected = checks.take(1), receipts = payload.receipts.take(1)))
      When("these alternatives request deterministic admission")
      val results = alternatives.map(A.assessC(validation(), _, "native-checks"))
      Then("no raw exit or selected-off action substitutes for complete successful behavioral checks")
      results.foreach(_.isSuccess shouldBe false)
      A.assessC(validation(), null, "native-checks").isSuccess shouldBe false
    }

    "reject forged or missing assessment and bypass of selected policy" in {
      Given("successful typed checks and an independently altered purported assessment")
      val a = assessment()
      val alternatives = Vector(None, Some(a.copy(branch = "detailed-review")), Some(a.copy(checks = Vector("other"))),
        Some(a.copy(candidate = candidate.copy(tree = "e" * 40))), Some(a.copy(validationReference = "crossed")))
      When("the native continuation recomputes assessment against the current immutable payload")
      val results = alternatives.map(value => N.observedStateC(validation(), payload.copy(assessment = value), "native-checks"))
      Then("neither a made-up admission nor the old checks-only event bypasses the selected Actions")
      results.foreach(_.isSuccess shouldBe false)
      validation().acceptC(O.ChecksObserved(candidate, checks.map(_.identity), "native-checks", true)).isSuccess shouldBe false
    }

    "go directly to required review and preserve immediate judgment obligations" in {
      Given("explicit required review or an unresolved immediate observation")
      val policies = Vector(policy.copy(detailedReview = "required"), policy.copy(observations = Vector(observation("immediate", None))))
      When("actual successful validation is assessed for each selected policy")
      val states = policies.map(p => assessed(validation(withPolicy(p))))
      Then("each issues the existing independent detailed review without intermediate triage or reviewless admission")
      states.foreach { state =>
        state.stage shouldBe O.Stage.Review
        state.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.ReviewStep
        state.stepAdmission shouldBe None
        state.reviewEvidence shouldBe None
      }
    }

    "retain eligible later work as unresolved while refusing invalid deferral" in {
      Given("an attributed unresolved question tied to a later planned Step")
      val deferred = policy.copy(observations = Vector(observation("release", Some("S2"))))
      When("the current Step's actual successful facts are assessed")
      val actual = assessment(validation(withPolicy(deferred)))
      Then("admission preserves the question source and reason without claiming that the observation was resolved")
      actual.branch shouldBe "admit"
      actual.policy.observations shouldBe deferred.observations
      val invalid = Vector(deferred.copy(observations = Vector(observation("release", Some("S1")))),
        deferred.copy(observations = Vector(observation("release", Some("absent")))),
        deferred.copy(observations = Vector(observation("release", None))))
      invalid.foreach(p => O.validatePlanC(withPolicy(p)).isSuccess shouldBe false)
    }

    "preserve the historical policy and wire records when no action policy was selected" in {
      Given("the unchanged historical required-review Plan and completed Step wire record")
      val old = plan.copy(steps = plan.steps.updated(0, plan.steps.head.copy(actionPolicy = None)))
      val state = validation(old)
      When("the existing native validation route consumes its historical checks without an assessment")
      val actual = take(N.observedStateC(state, payload, "native-checks"))
      val closed = O.ClosedStep("S1", O.Commit("d" * 40, base.head, candidate.tree), "independent-review")
      Then("review remains required and the legacy JSON has no manufactured admission")
      actual.stage shouldBe O.Stage.Review
      closed.asJson.hcursor.get[String]("review").toOption shouldBe Some("independent-review")
      closed.asJson.hcursor.downField("admission").focus shouldBe None
      closed.asJson.as[O.ClosedStep].toOption shouldBe Some(closed)
      N.codec.decode(N.codec.encode(payload)).toOption shouldBe Some(payload)
    }
  }

  "P65-Q1 closure authority and common Journal" should {
    "roundtrip old reviewed and reviewless admitted prefixes in exact Release continuation targets" in {
      Given("a marker-absent integrated repair target with either an old reviewed Step or its formal reviewless admission")
      Vector(false, true).foreach { reviewless =>
        val target = _release_target(reviewless)
        When("the saved Target JSON and declared public Record restore the same completed prefix")
        val wire = target.asJson
        val restored = wire.as[GoalPhaseReleaseRepairContinuation.Target]
        val publicrestored = take(GoalPhaseReleaseRepairContinuation.targetC(GoalPhaseReleaseRepairContinuation.targetRecord(target)))
        Then("the exact original Request commit and acceptance survive without inventing an independent review")
        restored shouldBe Right(target)
        publicrestored shouldBe target
        publicrestored.completed shouldBe target.completed
        publicrestored.originalRequest shouldBe target.originalRequest
        GoalPhaseReleaseRepairContinuation.validateTargetC(target).isSuccess shouldBe true
        val step = wire.hcursor.downField("completed").downArray
        step.downField("partialCompletion").focus shouldBe None
        if (reviewless) {
          step.downField("review").focus shouldBe Some(io.circe.Json.Null)
          step.downField("admission").focus should not be empty
          target.completed.head.review shouldBe None
          val omitted = wire.mapObject(_.add("completed", io.circe.Json.arr(
            step.focus.get.mapObject(_.remove("review")))))
          take(GoalPhaseReleaseRepairContinuation.targetC(GoalPhaseNativeMetadata.record(omitted))) shouldBe target
        } else {
          step.get[String]("review").toOption shouldBe Some("historical-step-review")
          step.downField("admission").focus shouldBe None
          step.focus.get.asObject.get.keys.toSet shouldBe Set("step", "commit", "review")
        }
      }
    }

    "retain exact partial completion chains with reviewed and formally admitted Release prefixes" in {
      for (reviewless <- Vector(false, true)) {
        Given("the exact completed Release prefix and its retained P to C to D commit chain")
        val original = _release_target(reviewless)
        val previous = original.completed.head
        val partial = O.Commit("e" * 40, previous.commit.parent, "f" * 40)
        val completion = previous.commit.copy(parent = partial.identity)
        val chain = O.StepCommitChain(original.base, partial, completion)
        O.validStepCommitChain(chain) shouldBe true
        val closed = previous.copy(commit = completion, partialCompletion = Some(chain))
        val request = W.inputCodec.decode(original.originalRequest).fold(message => fail(message), identity)
          .copy(completed = Vector(closed))
        val target = original.copy(completed = Vector(closed), originalRequest = W.inputCodec.encode(request))
        When("the strict Release codec and public target record read that original chain")
        val wire = target.asJson
        wire.as[GoalPhaseReleaseRepairContinuation.Target] shouldBe Right(target)
        take(GoalPhaseReleaseRepairContinuation.targetC(GoalPhaseReleaseRepairContinuation.targetRecord(target))) shouldBe target
        Then("acceptance provenance and every exact commit link remain present while malformed variants are refused")
        target.completed.head.review shouldBe previous.review
        target.completed.head.admission shouldBe previous.admission
        target.completed.head.partialCompletion shouldBe Some(chain)
        val step = wire.hcursor.downField("completed").downArray.focus.get
        val chainJson = step.hcursor.downField("partialCompletion").focus.get
        val invalid = Vector(step.mapObject(_.add("partialCompletion", io.circe.Json.Null)),
          step.mapObject(_.add("partialCompletion", chainJson.mapObject(_.add("unknownChain", true.asJson)))),
          step.mapObject(_.add("partialCompletion", chainJson.hcursor.downField("partial")
            .withFocus(_.mapObject(_.add("parent", ("9" * 40).asJson))).top.get)),
          step.mapObject(_.add("partialCompletion", chainJson.hcursor.downField("completion")
            .withFocus(_.mapObject(_.add("tree", ("8" * 40).asJson))).top.get)))
        invalid.foreach(value => wire.mapObject(_.add("completed", io.circe.Json.arr(value)))
          .as[GoalPhaseReleaseRepairContinuation.Target].isLeft shouldBe true)
      }
    }

    "refuse contradictory malformed and crossed completed acceptance in Release continuation targets" in {
      Given("a valid exact public Release target containing a reviewless formally admitted Step")
      val target = _release_target(true)
      val wire = target.asJson
      val step = wire.hcursor.downField("completed").downArray.focus.get
      val admission = step.hcursor.downField("admission").focus.get
      val malformed = Vector(step.mapObject(_.add("review", "fabricated-review".asJson)),
        step.mapObject(_.remove("admission")), step.mapObject(_.add("admission", io.circe.Json.Null)),
        step.mapObject(_.add("admission", io.circe.Json.True)),
        step.mapObject(_.add("admission", admission.mapObject(_.add("reference", "".asJson)))),
        step.mapObject(_.add("unknownAcceptance", io.circe.Json.True)),
        step.mapObject(_.add("commit", io.circe.Json.obj("identity" -> "wrong".asJson,
          "parent" -> base.head.asJson, "tree" -> candidate.tree.asJson))))
      When("the nested Target codec sees both or neither acceptance alternatives null or malformed admission and unknown or invalid commit fields")
      val results = malformed.map(value => wire.mapObject(_.add("completed", io.circe.Json.arr(value)))
        .as[GoalPhaseReleaseRepairContinuation.Target])
      val invalidstep = target.completed.head.copy(review = Some("fabricated-review"))
      val invalidtyped = target.copy(completed = Vector(invalidstep))
      val crossed = Vector(target.copy(generation = target.generation + 1),
        target.copy(candidate = target.candidate.copy(tree = "e" * 40)),
        target.copy(completed = Vector(target.completed.head.copy(step = "other-step"))))
      Then("neither decoding public projection nor typed validation can add acceptance or detach it from the saved owning Request")
      results.foreach(_.isLeft shouldBe true)
      malformed.foreach(value => GoalPhaseReleaseRepairContinuation.targetC(GoalPhaseNativeMetadata.record(
        wire.mapObject(_.add("completed", io.circe.Json.arr(value))))).isSuccess shouldBe false)
      GoalPhaseReleaseRepairContinuation.validateTargetC(invalidtyped).isSuccess shouldBe false
      crossed.foreach(value => GoalPhaseReleaseRepairContinuation.validateTargetC(value).isSuccess shouldBe false)
    }

    "carry exact admission through closure encoding and independent authority scope" in {
      Given("the exact formal admission at a native-shaped Closing record")
      val initial = take(InstanceRecord(InstanceIdentity("assessment-native"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
        initialRevision, Lifecycle.NotStarted, None, CorrelationReference("assessment-correlation"),
        CausationReference("assessment-start"), None, None, None, Vector.empty).validateC)
      val frontier = take(initial.appendC(initial.revision, GoalPhaseNativeRun.history(initial, "phase.Closing", "assessment")))
      val input = take(GoalPhaseNativeClosure.inputC(frontier, assessed()))
      val binding = io.circe.Json.obj("componentInstance" -> io.circe.Json.fromString("owner"), "scope" -> input.asJson)
      When("the ordinary closure codec and independent effect-target projection consume that actual input")
      val actual = NativeAuthorityScope.select(NativeExternalEvidence.Purpose.StepClosure, binding).toOption.get
      val wire = GoalPhaseNativeClosure.Payload(input, 1, None)
      Then("formal admission has its own authority target with no fake review reference")
      GoalPhaseNativeClosure.codec.decode(GoalPhaseNativeClosure.codec.encode(wire)).toOption shouldBe Some(wire)
      actual.hcursor.downField("scope").get[String]("admissionReference").toOption shouldBe Some(input.admission.get.reference)
      actual.hcursor.downField("scope").downField("reviewEvidence").focus shouldBe None
      val purpose = NativeExternalEvidence.Purpose.StepClosure
      val declared = NativeExternalEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("test", "host", "evidence")), "independent-host")
      val now = java.time.Instant.parse("2026-10-23T00:00:00Z")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
        "selected-target", "actual-grant", "1", declared.issuer, "participant", binding, now.toString)
      val stored = take(NativeExternalEvidence.decodeStorageC(take(NativeExternalEvidence.encodeStorageC(evidence))))
      NativeAuthorityScope.select(purpose, actual).toOption shouldBe Some(actual)
      stored.binding shouldBe actual
      NativeExternalEvidence.observeC(stored, declared, purpose, "selected-target", binding, now).isSuccess shouldBe true
      val differentReference = actual.mapObject(_.add("scope", actual.hcursor.downField("scope").focus.get
        .mapObject(_.add("admissionReference", io.circe.Json.fromString("foreign-admission")))))
      NativeExternalEvidence.observeC(stored.copy(binding = differentReference), declared, purpose,
        "selected-target", binding, now).isSuccess shouldBe false
      val crossed = binding.mapObject(_.add("scope", input.copy(candidate = candidate.copy(tree = "e" * 40)).asJson))
      NativeAuthorityScope.select(NativeExternalEvidence.Purpose.StepClosure, crossed).isRight shouldBe false
      val mixedReference = binding.mapObject(_.add("scope", input.asJson.mapObject(_.add("admissionReference",
        io.circe.Json.fromString(input.admission.get.reference)))))
      NativeAuthorityScope.select(purpose, mixedReference).isRight shouldBe false
      val mixed = binding.mapObject(_.add("scope", input.copy(reviewEvidence = Some("fake-review")).asJson))
      NativeAuthorityScope.select(NativeExternalEvidence.Purpose.StepClosure, mixed).isRight shouldBe false
    }

    "append idempotently and preserve unavailable conflict and indeterminate journal outcomes" in {
      Given("one actual-shaped assessment and a specification-owned common Journal Storage seam")
      val entry = J.Entry("step-assessment:native-checks", "repository", "6.5", "S1", "session", assessment(), "2026-10-23T00:00:00Z")
      final class Memory(failRead: Boolean = false, failCreate: Boolean = false) extends DevelopmentJournal.Storage {
        var row = Option.empty[Record]
        var creates = 0
        def loadC() = if (failRead) Consequence.stateConflict("specification unavailable") else Consequence.success(row)
        def createC(r: Record) = { creates += 1; if (failCreate) Consequence.stateConflict("specification uncertain") else {row = Some(r); Consequence.unit} }
      }
      val memory = new Memory()
      When("the immutable assessment is appended read projected and repeated")
      val recorded = take(J.appendC(memory, entry))
      val repeated = take(J.appendC(memory, entry.copy(recordedAt = "2026-10-23T00:00:01Z")))
      val stored = take(J.storedC(memory.row.get, entry.identity))
      Then("the real entry reference is retained once and failures do not rewrite native acceptance or pretend to have stored an entry")
      recorded.status shouldBe "recorded"
      repeated.status shouldBe "alreadyRecorded"
      memory.creates shouldBe 1
      stored shouldBe entry
      J.publicEntry(stored).assessment.nonEmpty shouldBe true
      J.publicEntry(stored).kind shouldBe "StepAssessment"
      take(J.appendC(memory, entry.copy(assessment = entry.assessment.copy(reason = "changed")))).status shouldBe "conflict"
      take(J.appendC(new Memory(failRead = true), entry)).status shouldBe "unavailable"
      take(J.appendC(new Memory(failCreate = true), entry)).status shouldBe "indeterminate"
      J.storedC(memory.row.get, entry.identity + "other").isSuccess shouldBe false
      J.rowC(entry.copy(assessment = entry.assessment.copy(policy = null))).isSuccess shouldBe false
      J.rowC(entry.copy(assessment = entry.assessment.copy(review = "fake-PASS"))).isSuccess shouldBe false
    }
  }
}
