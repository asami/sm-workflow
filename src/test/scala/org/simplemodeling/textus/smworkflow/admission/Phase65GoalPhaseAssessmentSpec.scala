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
import GoalPhaseNativeValidation as N
import GoalPhaseStepAssessment as A
import RepositorySyncNativeMergeValidation as R
import WorkflowSbtValidation as T
import DevelopmentJournalAssessment as J
import SplitPhaseExplicitPartitionFixture.take

/** Pure typed-boundary evidence; actual CLI/SBT/Git evidence is a separate suite.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
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
