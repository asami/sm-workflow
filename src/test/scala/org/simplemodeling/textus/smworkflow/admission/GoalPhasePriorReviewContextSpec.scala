package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeReview as R
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Pure actual-shaped completion material exercises report retention/projection.
  * Specification worker labels/evidence grant no independence, native completion
  * or installed acceptance.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhasePriorReviewContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _first = O.Snapshot("a" * 40, "c" * 40)
  private val _second = O.Snapshot("d" * 40, "e" * 40)
  private val _current = O.Snapshot("f" * 40, "1" * 40)
  private val _plan = O.Plan("prior-review-plan", "1", "PHASE-6.3", "sm-workflow",
    Vector(O.Step("S1", "First change", Vector("one.scala"), Vector.empty, Vector("focused-one")),
      O.Step("S2", "Second change", Vector("two.scala"), Vector("S1"), Vector("focused-two"))), Vector("integration"))
  private val _check = RepositorySyncNativeMergeValidation.Receipt("focused-one", "exited", Some(0), "selected result", "")
  private val _context = R.Context(Vector("one.scala"), "current accumulated diff", Vector(_check))
  private val _handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("specification-prior-context"), InstanceIdentity("specification-instance"))

  private def _request(kind: String, step: Option[String], candidate: O.Snapshot, generation: Long = 1): S.Request =
    S.Request(_handle.instanceIdentity.value, "7", kind, step, generation, "Specification assigned work", _plan,
      _base, Vector.empty, Some(candidate), Some("checks-" + step.getOrElse("phase") + "-" + generation), None, Some(_context))
  private def _submitted(request: S.Request, value: S.Result, identity: String, revision: String): ContinuationResult[S.Result] = {
    val reference = ContextReference(identity, revision)
    ContinuationResult(_handle, StateMachineRunIdentity("specification-run"), ContinuationIdentity("specification-slot-" + request.kind),
      StateMachineRevision("8"), ContextSnapshot(_handle.workflowRevision.value), TypedValue(S.resultCodec.typeIdentity, value),
      reference, Vector.empty, ExecutionEvidence(Vector(reference), Some(if (request.kind.startsWith("Review"))
        "specification-independent-reviewer" else "specification-implementer")))
  }
  private def _accepted(request: S.Request, identity: String, revision: String): ContinuationResult[S.Result] =
    _submitted(request, S.Result("accept", "Actual-shaped independent report for " + request.stepIdentity.getOrElse("Phase"), Vector.empty),
      identity, revision)
  private def _prior(request: S.Request, submitted: ContinuationResult[S.Result]): R.Prior =
    take(R.retainPriorC(Vector.empty, request, submitted)).head
  private def _json(record: Record): Json = io.circe.parser.parse(take(record.toJsonStringC)).toOption.get

  "Actual prior review business context" should {
    "project the accepted Step sequence into Phase integration and keep the Phase report for corrective Step review" in {
      Given("Spec: docs/spec/native-goal-command-task.md; accepted actual-shaped Step1 and Step2 replies with their original independent-review material")
      val first = _request("ReviewStep", Some("S1"), _first)
      val second = _request("ReviewStep", Some("S2"), _second)
      val firstresult = _accepted(first, "step-one-result", "8")
      val secondresult = _accepted(second, "step-two-result", "12")
      When("the pure native owner retains each admitted completion and prepares Phase business material")
      val one = take(R.retainPriorC(Vector.empty, first, firstresult))
      val both = take(R.retainPriorC(one, second, secondresult))
      val integration = take(R.priorReviewsForC(both, "ReviewPhase", None))
      Then("both real reports retain their original target, checks, generation, result and chronological order")
      integration shouldBe Vector(R.Prior("ReviewStep", Some("S1"), 1, _first, first.checksReceipt.get,
        firstresult.resultReference.identity, firstresult.resultReference.revision, firstresult.result.value),
        R.Prior("ReviewStep", Some("S2"), 1, _second, second.checksReceipt.get,
          secondresult.resultReference.identity, secondresult.resultReference.revision, secondresult.result.value))

      Given("an actual-shaped Phase report requiring corrective work after those two accepted Steps")
      val phase = _request("ReviewPhase", None, _current)
      val phaseresult = _submitted(phase, S.Result("repair", "Integration needs an affected correction", Vector("Preserve S1 integration")),
        "phase-review-result", "17")
      When("the admitted Phase report is retained and the affected Step1 re-review is projected")
      val all = take(R.retainPriorC(both, phase, phaseresult))
      val corrective = take(R.priorReviewsForC(all, "ReviewStep", Some("S1")))
      Then("Step1 receives its original accepted report and the real Phase findings without inheriting Step2's target")
      corrective shouldBe Vector(integration.head, _prior(phase, phaseresult))
      corrective.last.result.findings shouldBe Vector("Preserve S1 integration")
      take(R.priorReviewsForC(all, "ReviewPhase", None)) shouldBe all
    }

    "replace only the same Step's latest result and append its exact newer original target in completion order" in {
      Given("Spec: docs/spec/native-goal-command-task.md; a repaired Step1 report followed by an accepted Step2 report")
      val first = _request("ReviewStep", Some("S1"), _first)
      val second = _request("ReviewStep", Some("S2"), _second)
      val failed = _submitted(first, S.Result("repair", "First finding", Vector("Correct the affected branch")), "old-s1", "8")
      val secondresult = _accepted(second, "original-s2", "12")
      val before = take(R.retainPriorC(take(R.retainPriorC(Vector.empty, first, failed)), second, secondresult))
      val revised = _request("ReviewStep", Some("S1"), _current, 2)
      val revisedresult = _accepted(revised, "new-s1", "16")
      When("a later admitted Step1 result replaces the same kind/Step key")
      val after = take(R.retainPriorC(before, revised, revisedresult))
      Then("Step2's original report remains exact and only the latest Step1 report is appended")
      after shouldBe Vector(before(1), _prior(revised, revisedresult))
      after.last.candidate shouldBe _current
      after.last.checksReceipt shouldBe revised.checksReceipt.get
      after.last.resultIdentity shouldBe "new-s1"
      after.last.resultRevision shouldBe "16"
      after.last.generation shouldBe 2
      before.head.candidate shouldBe _first
      before.head.resultIdentity shouldBe "old-s1"
      before.head.result.findings shouldBe Vector("Correct the affected branch")
    }

    "leave prior reviews unchanged for implementation and review-origin repair completion" in {
      Given("Spec: docs/spec/native-goal-command-task.md; one real report and semantic implementation/repair replies")
      val reviewed = _request("ReviewStep", Some("S1"), _first)
      val priors = Vector(_prior(reviewed, _accepted(reviewed, "review-before-work", "8")))
      val implementation = reviewed.copy(kind = "ImplementStep", candidate = None, checksReceipt = None, review = None)
      val repair = implementation.copy(kind = "RepairStep", repairDirective = Some(O.RepairDirective("Assigned correction", Vector("Fix S1"))),
        review = Some(_context.copy(priorReviews = priors)))
      Vector(implementation, repair).foreach { request =>
        Given("the assigned " + request.kind + " completion with no independent review result")
        When("the retention helper consumes the already-admitted semantic completion")
        val retained = take(R.retainPriorC(priors, request,
          _submitted(request, S.Result("completed", "Assigned work completed", Vector.empty), "semantic-result", "9")))
        Then("it creates no review and preserves the entire prior vector")
        retained shouldBe priors
      }
    }

    "add the just-admitted review to its unchanged repair context without retargeting previous reports" in {
      Given("Spec: docs/spec/native-goal-command-task.md; existing Phase findings and an affected Step review with its original diff and checks")
      val phase = _request("ReviewPhase", None, _second)
      val phaseresult = _submitted(phase, S.Result("repair", "Prior integration rationale", Vector("Affected S1 integration")), "prior-phase", "8")
      val phaseprior = _prior(phase, phaseresult)
      val context = _context.copy(priorReviews = Vector(phaseprior))
      val request = _request("ReviewStep", Some("S1"), _first).copy(review = Some(context))
      val submitted = _submitted(request, S.Result("repair", "Actual Step rationale", Vector("Correct S1 behavior")), "actual-step", "12")
      When("review-origin repair retains previous material plus this admitted report using the same pure owner")
      val priors = take(R.retainPriorC(context.priorReviews, request, submitted))
      val repaircontext = context.copy(priorReviews = priors)
      Then("the original context stays exact and contains both actual reports with their original target references and findings")
      repaircontext.copy(priorReviews = context.priorReviews) shouldBe context
      repaircontext.priorReviews shouldBe Vector(phaseprior, _prior(request, submitted))
      repaircontext.priorReviews.map(_.candidate) shouldBe Vector(_second, _first)
      repaircontext.priorReviews.map(_.resultIdentity) shouldBe Vector("prior-phase", "actual-step")
      repaircontext.priorReviews.last.result.findings shouldBe Vector("Correct S1 behavior")
    }

    "retain at most 64 Step reports plus one Phase report and allow replacement at that bound" in {
      Given("Spec: docs/spec/native-goal-command-task.md; the latest actual-shaped reports for 64 distinct Steps and one Phase")
      val request = _request("ReviewStep", Some("S1"), _first)
      val submitted = _accepted(request, "latest-first-step", "20")
      val first = _prior(request, _accepted(request, "first-step", "8"))
      val steps = (1 to 64).map(index => first.copy(step = Some("S" + index), resultIdentity = "step-result-" + index)).toVector
      val phase = first.copy(kind = "ReviewPhase", step = None, resultIdentity = "phase-result")
      val complete = steps :+ phase
      When("the latest Step1 result replaces its bounded key and Phase integration projects the reports")
      val replaced = take(R.retainPriorC(complete, request, submitted))
      val projected = take(R.priorReviewsForC(replaced, "ReviewPhase", None))
      Then("all 65 unique reports remain with the replaced Step appended chronologically")
      projected shouldBe complete.tail :+ _prior(request, submitted)
      projected.size shouldBe 65
      projected.count(_.kind == "ReviewPhase") shouldBe 1

      Given("a further distinct Step would exceed the maximum selected Step count")
      val extra = request.copy(stepIdentity = Some("S65"))
      When("retention attempts to append that otherwise valid report")
      val overflow = R.retainPriorC(complete, extra, _accepted(extra, "overflow-step", "21"))
      Then("it refuses without dropping any original report to make room")
      overflow.isSuccess shouldBe false
      complete shouldBe steps :+ phase
    }

    "keep a different current candidate and current diff/checks separate from exact public prior report Records" in {
      Given("Spec: docs/spec/native-goal-command-task.md; a prior finding against Step1 and a new current candidate for affected review")
      val original = _request("ReviewStep", Some("S1"), _first)
      val result = _submitted(original, S.Result("repair", "Original rationale", Vector("Original concrete finding")), "prior-result", "8")
      val priors = Vector(_prior(original, result))
      val current = _request("ReviewStep", Some("S1"), _current, 2).copy(
        review = Some(_context.copy(priorReviews = take(R.priorReviewsForC(priors, "ReviewStep", Some("S1"))))))
      When("normal public output projects and the generated DTO reads its declared Record vector")
      val publicvalue = NativeWorkflowOperationOutput.goalWork(current)
      val decoded = take(V.GoalPhaseWorkView.createC(take(new RecordDecoder().json(take(publicvalue.toRecord().toJsonStringC)))))
      Then("the public records contain each complete original report while current review data stays current")
      publicvalue.review.get.priorReviews.map(_json) shouldBe priors.map(_.asJson)
      decoded.review.get.priorReviews.map(_json) shouldBe priors.map(_.asJson)
      decoded.review.get.diff shouldBe current.review.get.diff
      decoded.review.get.checks.head.identity shouldBe _check.identity
      decoded.candidate.get.head shouldBe _current.head
      decoded.candidate.get.tree shouldBe _current.tree
      priors.head.candidate shouldBe _first
      priors.head.result shouldBe result.result.value
      priors.head.resultIdentity shouldBe result.resultReference.identity
      priors.head.checksReceipt shouldBe original.checksReceipt.get
    }

    "reject malformed, duplicate and overflowing prior history and invalid actual result references" in {
      Given("Spec: docs/spec/native-goal-command-task.md; one valid original review report and malformed retained alternatives")
      val request = _request("ReviewStep", Some("S1"), _first)
      val submitted = _accepted(request, "review-reference", "8")
      val prior = _prior(request, submitted)
      val invalid = Vector(null, prior.copy(kind = "ImplementStep"), prior.copy(step = None), prior.copy(step = Some("")),
        prior.copy(step = Some("bad\nstep")), prior.copy(step = null), prior.copy(kind = "ReviewPhase"),
        prior.copy(generation = 0), prior.copy(candidate = null), prior.copy(candidate = _first.copy(tree = "invalid")),
        prior.copy(checksReceipt = ""), prior.copy(checksReceipt = "x" * 16385),
        prior.copy(resultIdentity = ""), prior.copy(resultIdentity = "bad\nreference"),
        prior.copy(resultRevision = "0"), prior.copy(resultRevision = "01"), prior.copy(resultRevision = "+1"),
        prior.copy(resultRevision = "9223372036854775808"), prior.copy(result = null),
        prior.copy(result = prior.result.copy(outcome = "completed")), prior.copy(result = prior.result.copy(summary = "")),
        prior.copy(result = prior.result.copy(outcome = "repair")),
        prior.copy(result = prior.result.copy(findings = Vector("accept cannot carry a finding"))),
        prior.copy(result = prior.result.copy(reviewObservations = null)),
        prior.copy(result = prior.result.copy(acceptance = Some(GoalPhaseAcceptanceBoundary.Report(null, Vector.empty)))))
      invalid.zipWithIndex.foreach { (value, index) =>
        Given("malformed prior report case " + index)
        When("the pure retention owner consumes existing history")
        val refused = R.retainPriorC(Vector(value), request, submitted)
        Then("the malformed report cannot enter current review material")
        refused.isSuccess shouldBe false
      }
      val histories = Vector(null, Vector(prior, prior), (1 to 65).map(i => prior.copy(step = Some("S" + i))).toVector,
        (1 to 66).map(i => prior.copy(step = Some("S" + i))).toVector)
      histories.zipWithIndex.foreach { (history, index) =>
        Given("null, duplicate or overflowing review history case " + index)
        When("retention validates the bounded unique kind/Step keys")
        val refused = R.retainPriorC(history, request, submitted)
        Then("invalid history refuses before appending any report")
        refused.isSuccess shouldBe false
      }
      val references = Vector(ContextReference("", "8"), ContextReference("r" * 16385, "8"),
        ContextReference("review-reference", "0"), ContextReference("review-reference", "01"),
        ContextReference("review-reference", "invalid"), null)
      references.zipWithIndex.foreach { (reference, index) =>
        Given("an invalid actual completion result reference case " + index)
        When("retention reads that exact submitted result reference")
        val refused = R.retainPriorC(Vector.empty, request, submitted.copy(resultReference = reference))
        Then("no normalized or synthetic reference is substituted")
        refused.isSuccess shouldBe false
      }

      val requests = Vector(null, request.copy(kind = "UnassignedReview"), request.copy(candidate = None),
        request.copy(candidate = null), request.copy(checksReceipt = None), request.copy(checksReceipt = null),
        request.copy(stepIdentity = None), request.copy(generation = 0))
      requests.zipWithIndex.foreach { (input, index) =>
        Given("an invalid original assigned review input case " + index)
        When("retention is given a request without the exact required original target facts")
        val refused = R.retainPriorC(Vector.empty, input, submitted)
        Then("a request-only placeholder cannot become a prior review report")
        refused.isSuccess shouldBe false
      }
      val replies = Vector(null, submitted.copy(result = null),
        submitted.copy(result = TypedValue(S.resultCodec.typeIdentity, null)),
        submitted.copy(result = TypedValue("OtherResult", submitted.result.value)))
      replies.zipWithIndex.foreach { (reply, index) =>
        Given("a missing or mistyped actual completion result case " + index)
        When("retention receives no valid actual submitted result value")
        val refused = R.retainPriorC(Vector.empty, request, reply)
        Then("it synthesizes no report from the assigned request")
        refused.isSuccess shouldBe false
      }
    }

    "accept only an old empty issued prior vector while requiring every other Context field to match exactly" in {
      Given("Spec: docs/spec/native-goal-command-task.md; an old issued Context and newly reconstructed actual prior report")
      val request = _request("ReviewStep", Some("S1"), _first)
      val prior = _prior(request, _accepted(request, "issued-review-reference", "8"))
      val expected = _context.copy(priorReviews = Vector(prior))
      When("the named comparison checks an empty old vector, an exact new vector and absent Context")
      val old = R.sameIssuedContext(Some(_context), Some(expected))
      val exact = R.sameIssuedContext(Some(expected), Some(expected))
      Then("only optional prior business context may be absent on already issued work")
      old shouldBe true
      exact shouldBe true
      R.sameIssuedContext(None, None) shouldBe true
      R.sameIssuedContext(None, Some(expected)) shouldBe false
      R.sameIssuedContext(Some(expected), None) shouldBe false
      R.sameIssuedContext(Some(expected), Some(_context)) shouldBe false

      val metadata = GoalPhaseNativeMetadataFixture.prepared._2
      val mismatches = Vector(expected.copy(priorReviews = Vector(prior.copy(result = S.Result("repair", "Changed findings", Vector("New finding"))))),
        expected.copy(priorReviews = Vector(prior.copy(candidate = _second))),
        expected.copy(priorReviews = Vector(prior.copy(resultIdentity = "different-result"))),
        expected.copy(priorReviews = Vector(prior.copy(resultRevision = "9"))),
        expected.copy(priorReviews = Vector(prior.copy(checksReceipt = "different-prior-checks"))),
        expected.copy(metadata = metadata), expected.copy(diff = "different current diff"),
        expected.copy(checks = Vector(_check.copy(stdout = "different current checks"))),
        expected.copy(changedPaths = Vector("other.scala")), expected.copy(priorReviews = null),
        _context.copy(metadata = metadata), _context.copy(diff = "changed old diff"),
        _context.copy(checks = Vector(_check.copy(exitCode = Some(1)))))
      mismatches.zipWithIndex.foreach { (issued, index) =>
        Given("an issued Context mismatch case " + index)
        When("the retained issue is compared with actual expected review material")
        val matches = R.sameIssuedContext(Some(issued), Some(expected))
        Then("nonempty report, target, reference, metadata, path, diff or check changes refuse")
        matches shouldBe false
      }
    }
  }
}
