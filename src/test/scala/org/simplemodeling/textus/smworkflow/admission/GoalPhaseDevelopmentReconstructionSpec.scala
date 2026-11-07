package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseDevelopmentReconstruction as R

/** Pure reconstruction examples describe controller and business-data contracts;
  * only the native CLI specification supplies actual effects and Human evidence.
  * @since   Nov.  5, 2026
  * @version Nov.  7, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.AdmissionTestDeclaration])
final class GoalPhaseDevelopmentReconstructionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail("Reconstruction example failed: " + failure)
  }
  private def _oid(value: Char): String = value.toString * 40
  private val _base = O.Snapshot(_oid('a'), _oid('b'))
  private val _plan = O.Plan("current-plan", "1", "COMMON", "repository",
    Vector(O.Step("first", "First accepted boundary", Vector("src/first.scala"), Vector.empty, Vector("first-test")),
      O.Step("second", "Remaining development", Vector("src/second.scala"), Vector("first"), Vector("second-test"))),
    Vector("phase-test"), repairLimit = 2)
  private def _initial: O.State = _take(O.startC(_plan, _base, Some(GoalPhaseRuntimeRepairControl.version)))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _ready(state: O.State): O.State = _take(state.acceptC(O.SemanticCompleted(_key(state), "Assigned work completed")))
  private def _checked(state: O.State, tree: Char): O.State = {
    val observed = _take(_ready(state).acceptC(O.CandidateObserved(O.Snapshot(state.tipSnapshot.head, _oid(tree)))))
    _take(observed.acceptC(O.ChecksObserved(observed.candidate.get, state.plan.steps(state.cursor).checks, "actual-check", true)))
  }
  private def _first_closed: O.State = {
    val reviewed = _checked(_initial, 'c')
    val accepted = _take(reviewed.acceptC(O.ReviewAdmitted(_key(reviewed), reviewed.candidate.get,
      "actual-check", "independent-first-review", O.ReviewOutcome.Accept)))
    val pending = _take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "first-intent")))
    _take(pending.acceptC(O.StepCommitObserved("first-intent", O.Commit(_oid('d'), _base.head, _oid('c')))))
  }
  private def _observation(state: O.State, tree: String, dirty: Vector[GoalPhaseCandidateObservationContinuation.Dirty] = Vector.empty): R.Observation =
    R.Observation("/specification/repository", "main", state.tipSnapshot.head, state.tipSnapshot.tree, tree, dirty)
  private def _assessment(state: O.State, plan: O.Plan, observed: R.Observation,
    unstarted: Boolean = false, uncertainty: Vector[String] = Vector.empty): R.Assessment =
    R.assess(state, plan, _plan.phaseOwnedPaths, Vector("first-test", "second-test"), Vector("phase-test"),
      observed, state.candidate, unstarted, None, uncertainty)
  private def _payload(state: O.State, plan: O.Plan, assessment: R.Assessment): R.Payload = {
    val raw = plan.copy(effectiveAcceptance = None)
    R.Payload("same-session", "20", "participant", "component", ContextReference("goal-phase", "1"),
      R.Documents("docs/phase.md", "docs/checklist.md", "Current actual prose Phase\n",
        "- [x] observed: Actual checklist text\n", GoalPhasePlanRevision.Captured(
          GoalPhasePlanRevision.Proposal("projection", "1", "Actual current projection", raw), plan)),
      _observation(state, state.candidate.map(_.tree).getOrElse(state.tipSnapshot.tree)), assessment,
      "Continue current source in the same Session", None, Some(ContextReference("retained-result", "1")), None, None,
      _plan.phaseOwnedPaths)
  }

  "Common development reconstruction" should {
    "apply an adopted remaining insertion with no repair reservation and retain the accepted prefix" in {
      Given("an accepted first Step and an ordinary unclosed second Step")
      val state = _first_closed
      val inserted = O.Step("preparation", "Prepare remaining work", Vector("src/second.scala"),
        Vector("first"), Vector("second-test"))
      val proposed = state.plan.copy(revision = "inserted", steps =
        Vector(state.plan.steps.head, inserted, state.plan.steps(1)))
      val assessment = _assessment(state, proposed, _observation(state, state.tipSnapshot.tree))
      val payload = _payload(state, proposed, assessment)
      val waiting = _take(R.selectedStateC(state, payload))
      val answer = ContextReference("actual-insertion-answer", "1")
      When("the actual offered Human direction is applied through the retained selection")
      R.choicesFor(payload) should contain("AdoptDevelopmentReconstruction")
      val selected = _take(R.selectedStateC(waiting, payload.copy(humanAnswer = Some(answer)),
        Some("AdoptDevelopmentReconstruction" -> answer)))
      Then("the inserted Step is next and the accepted evidence and accounting remain intact")
      selected.plan shouldBe proposed
      selected.completed shouldBe state.completed
      selected.base shouldBe state.base
      selected.repairs shouldBe state.repairs
      selected.repairObservations shouldBe state.repairObservations
      selected.next.asInstanceOf[O.Semantic].key.step shouldBe Some("preparation")
    }

    "withhold adoption and reject moving an actual repair scope" in {
      Given("a failed second Step with a charged repair and retained observation")
      val observed = _take(_ready(_first_closed).acceptC(O.CandidateObserved(O.Snapshot(_oid('d'), _oid('e')))))
      val repair = _take(observed.acceptC(O.RepairableChecksObserved(observed.candidate.get, Vector("second-test"),
        "failed-second", O.RepairDirective("Fix failure", Vector("actual failure")))))
      val state = _ready(repair)
      val inserted = O.Step("preparation", "Prepare remaining work", Vector("src/second.scala"),
        Vector("first"), Vector("second-test"))
      val proposed = state.plan.copy(revision = "inserted", steps = Vector(state.plan.steps.head, inserted, state.plan.steps(1)))
      When("assessment and application encounter the same changed semantic boundary")
      val assessment = _assessment(state, proposed, _observation(state, state.tipSnapshot.tree))
      val payload = _payload(state, proposed, assessment)
      Then("the actual repair history prevents offering or applying the move")
      assessment.incompatible should contain("remaining Step would move a retained repair scope")
      R.choicesFor(payload) should not contain "AdoptDevelopmentReconstruction"
      state.reconstructDevelopmentC(proposed, true, None).isSuccess shouldBe false
      state.repairs should be > 0
      state.repairObservations should not be empty
    }

    "protect a pending repair after its available batch has been charged" in {
      Given("a failed current Step with one available repair batch")
      val plan = _plan.copy(repairLimit = 1)
      val initial = _take(O.startC(plan, _base, Some(GoalPhaseRuntimeRepairControl.version)))
      val observed = _take(_ready(initial).acceptC(O.CandidateObserved(O.Snapshot(_base.head, _oid('c')))))
      val repair = _take(observed.acceptC(O.RepairableChecksObserved(observed.candidate.get, Vector("first-test"),
        "failed-first", O.RepairDirective("Fix failure", Vector("actual failure")))))
      val retried = _take(_ready(repair).acceptC(O.CandidateObserved(O.Snapshot(_base.head, _oid('e')))))
      val stopped = _take(retried.acceptC(O.RepairableChecksObserved(retried.candidate.get, Vector("first-test"),
        "failed-again", O.RepairDirective("Fix failure", Vector("actual failure")))))
      val inserted = O.Step("preparation", "Prepare first work", Vector("src/first.scala"), Vector.empty, Vector("first-test"))
      val proposed = plan.copy(revision = "inserted", steps = inserted +: plan.steps)
      When("the stopped boundary is assessed for an insertion")
      val assessment = _assessment(stopped, proposed, _observation(stopped, stopped.tipSnapshot.tree))
      Then("the proposal is incompatible and direct application refuses it")
      stopped.stage shouldBe O.Stage.Decision
      stopped.pendingRepair should not be empty
      assessment.incompatible should contain("remaining Step would move a retained repair scope")
      R.choicesFor(_payload(stopped, proposed, assessment)) should not contain "AdoptDevelopmentReconstruction"
      stopped.reconstructDevelopmentC(proposed, true, None).isSuccess shouldBe false

      When("a revised proposal leaves the stopped Step identity unchanged")
      val revised = plan.copy(revision = "same-step-revision")
      val revisedAssessment = _assessment(stopped, revised, _observation(stopped, stopped.tipSnapshot.tree))
      Then("assessment and application both refuse amendment of the stopped reservation")
      revisedAssessment.incompatible should contain("stopped repair reservation cannot be amended")
      R.choicesFor(_payload(stopped, revised, revisedAssessment)) should not contain "AdoptDevelopmentReconstruction"
      stopped.reconstructDevelopmentC(revised, true, None).isSuccess shouldBe false
    }

    "retain exact legacy v1 bytes and require complete versioned current-prefix proof for v2" in {
      Given("one legacy selection and a new current-source partition with distinct retained direction and failed-result references")
      val state = _ready(_first_closed)
      val assessment = R.Assessment(Vector.empty, Vector.empty, Vector.empty, true, false)
      val legacy = _payload(state, state.plan, assessment)
      val direction = ContextReference("same-session:ordered-source-history-normalization:10", "1")
      val proof = R.PrefixSelection(R.prefixVersion, direction,
        ContextReference(direction.identity + ":reconciliation-result", "1"), Vector("src/first.scala"),
        Vector("src/second.scala"), state.generation, state.generation + 1)
      val current = legacy.copy(prefixSelection = Some(proof))
      When("the durable codec reads both generations of the common selection")
      val oldwire = R.codec.encode(legacy)
      val wire = R.codec.encode(current)
      Then("v1 omits the new field and roundtrips byte-identically while v2 requires its complete exact proof")
      oldwire.hcursor.get[String]("schemaVersion") shouldBe Right(R.schema)
      oldwire.hcursor.downField("payload").downField("prefixSelection").focus shouldBe None
      R.codec.decode(oldwire) shouldBe Right(legacy)
      R.codec.encode(R.codec.decode(oldwire).toOption.get).noSpaces shouldBe oldwire.noSpaces
      wire.hcursor.get[String]("schemaVersion") shouldBe Right(R.prefixSchema)
      R.codec.decode(wire) shouldBe Right(current)
      val missing = wire.hcursor.downField("payload").withFocus(_.mapObject(_.remove("prefixSelection"))).top.get
      val absent = wire.hcursor.downField("payload").withFocus(_.mapObject(_.add("prefixSelection", Json.Null))).top.get
      val crossed = wire.hcursor.downField("payload").downField("prefixSelection")
        .withFocus(_.mapObject(_.add("failedResult", Json.obj("identity" -> Json.fromString("unrelated-intent"),
          "revision" -> Json.fromString("1"))))).top.get
      val unknown = wire.hcursor.downField("payload").downField("prefixSelection")
        .withFocus(_.mapObject(_.add("extraProof", Json.True))).top.get
      val nullquestion = wire.hcursor.downField("payload").downField("prefixSelection")
        .withFocus(_.mapObject(_.add("supersededQuestion", Json.Null))).top.get
      Vector(missing, absent, crossed, unknown, nullquestion,
        wire.mapObject(_.add("schemaVersion", Json.fromString(R.schema))),
        wire.hcursor.downField("payload").downField("prefixSelection")
          .withFocus(_.mapObject(_.add("version", Json.fromString("unknown")))).top.get).foreach(value =>
        R.codec.decode(value).isLeft shouldBe true)
    }

    "resolve only an authenticated technical inspection Decision with ordinary generation and unchanged repair accounting" in {
      Given("an accepted first Step and an ordinary consumed repair whose completed work reached inspection")
      val observed = _take(_ready(_first_closed).acceptC(O.CandidateObserved(O.Snapshot(_oid('d'), _oid('e')))))
      val repair = _take(observed.acceptC(O.RepairableChecksObserved(observed.candidate.get, Vector("second-test"),
        "retained-second-failure", O.RepairDirective("Repair current source", Vector("Original finding")))))
      val inspected = _ready(repair)
      val waiting = _take(inspected.reconstructDevelopmentC(inspected.plan, false, None,
        Vector("Original normalization owner remains unresolved")))
      val direction = ContextReference("same-session:ordered-source-history-normalization:10", "1")
      val proof = R.PrefixSelection(R.prefixVersion, direction,
        ContextReference(direction.identity + ":reconciliation-result", "1"), Vector.empty, Vector("src/second.scala"),
        waiting.generation, waiting.generation + 1, Some(GoalPhaseSourceHistoryNormalization.Question(
          "same-session:ordered-human:19", "20")))
      val payload = _payload(waiting, waiting.plan, R.Assessment(Vector.empty, Vector.empty, Vector.empty, true, false))
      When("the native-authenticated v2 connection selects a new subject while the unchanged default connection is also evaluated")
      val selected = _take(R.selectedStateC(waiting, payload.copy(prefixSelection = Some(proof))))
      val default = _take(waiting.reconstructDevelopmentC(waiting.plan, true, None))
      Then("only the explicit technical flag resolves Decision and every accepted fact and consumed repair remains")
      default.stage shouldBe O.Stage.Decision
      default.generation shouldBe waiting.generation
      default.decision shouldBe waiting.decision
      selected.stage shouldBe O.Stage.InspectCandidate
      selected.generation shouldBe waiting.generation + 1
      selected.plan shouldBe waiting.plan
      selected.base shouldBe waiting.base
      selected.completed shouldBe waiting.completed
      selected.repairs shouldBe waiting.repairs
      selected.ordinaryRepairs shouldBe waiting.ordinaryRepairs
      selected.pendingRepair shouldBe waiting.pendingRepair
      selected.repairControl shouldBe waiting.repairControl
      selected.repairObservations shouldBe waiting.repairObservations
      selected.repairDirective shouldBe waiting.repairDirective
      selected.decision shouldBe None
      selected.decisionSource shouldBe None
      selected.candidate shouldBe None
      selected.checksReceipt shouldBe None
      selected.reviewEvidence shouldBe None

      Given("a business Implementation Decision and a distinct real closure intent")
      val business = _take(_initial.reconstructDevelopmentC(_initial.plan, false, None, Vector("Human business meaning remains")))
      val checked = _checked(_initial, 'c')
      val accepted = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
        "actual-check", "independent-review", O.ReviewOutcome.Accept)))
      val closing = _take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "unrelated-unknown-commit")))
      When("the same technical connection is attempted outside its inspection boundary")
      val refused = Vector(business, closing).map(state => state.reconstructDevelopmentC(state.plan, true, None,
        technicalReassessment = true))
      Then("neither unrelated uncertainty nor a meaningful Human question gains replay or another repair allowance")
      refused.foreach(_.isSuccess shouldBe false)
      business.decisionSource shouldBe Some(O.Stage.Implementation)
      closing.closureIntent shouldBe Some("unrelated-unknown-commit")
    }

    "resolve either actual document choice while keeping applicable original evidence for Keep" in {
      Given("a checked original candidate and a proposed remaining dependency clarification")
      val state = _checked(_first_closed, 'e')
      val proposed = state.plan.copy(revision = "document-question", steps = state.plan.steps.updated(1,
        state.plan.steps(1).copy(dependencies = Vector.empty)))
      val assessment = _assessment(state, proposed, _observation(state, state.candidate.get.tree))
      val payload = _payload(state, proposed, assessment)
      val waiting = _take(R.selectedStateC(state, payload))
      val reference = ContextReference("actual-document-answer", "1")
      val answered = payload.copy(humanAnswer = Some(reference))
      When("each independently authenticated choice selects through the same common controller connection")
      val kept = _take(R.selectedStateC(waiting, answered, Some("KeepCurrentRequirements" -> reference)))
      val adopted = _take(R.selectedStateC(waiting, answered, Some("AdoptDevelopmentReconstruction" -> reference)))
      Then("Keep resolves the question with the original candidate generation checks and review requirements intact")
      waiting.stage shouldBe O.Stage.Decision
      kept.plan shouldBe state.plan
      kept.stage shouldBe state.stage
      kept.generation shouldBe state.generation
      kept.candidate shouldBe state.candidate
      kept.checksReceipt shouldBe state.checksReceipt
      kept.reviewEvidence shouldBe state.reviewEvidence
      kept.decision shouldBe None
      kept.completed shouldBe state.completed
      kept.base shouldBe state.base
      kept.repairControl shouldBe state.repairControl
      adopted.plan shouldBe proposed
      adopted.stage shouldBe O.Stage.InspectCandidate
      adopted.generation shouldBe state.generation + 1
      adopted.candidate shouldBe None
      adopted.repairs shouldBe state.repairs

      Given("the same original requirements with stored observation of later dirty current source")
      val dirty = Vector(GoalPhaseCandidateObservationContinuation.Dirty("M", "M", "src/second.scala", None))
      val observed = _observation(state, _oid('f'), dirty)
      val changed = _payload(state, proposed, _assessment(state, proposed, observed)).copy(observation = observed)
      val changedwaiting = _take(R.selectedStateC(state, changed))
      When("Keep selects the original requirements against the actual changed source rather than the rejected proposal")
      val fresh = _take(R.selectedStateC(changedwaiting, changed.copy(humanAnswer = Some(reference)),
        Some("KeepCurrentRequirements" -> reference)))
      Then("only stale source projections are cleared for fresh ordinary capture with original requirements and accounting")
      fresh.plan shouldBe state.plan
      fresh.stage shouldBe O.Stage.InspectCandidate
      fresh.generation shouldBe state.generation + 1
      fresh.candidate shouldBe None
      fresh.checksReceipt shouldBe None
      fresh.reviewEvidence shouldBe None
      fresh.completed shouldBe state.completed
      fresh.repairs shouldBe state.repairs
      fresh.repairControl shouldBe state.repairControl
    }

    "escape the candidate-less initial source generation on Keep while retaining a later unstarted capture" in {
      Given("pure model evidence of completed current semantic work after one consumed repair and an accepted Step prefix")
      val observed = _take(_ready(_first_closed).acceptC(O.CandidateObserved(O.Snapshot(_oid('d'), _oid('e')))))
      val repair = _take(observed.acceptC(O.RepairableChecksObserved(observed.candidate.get, Vector("second-test"),
        "initial-source-failure", O.RepairDirective("Correct current source", Vector("Retained model finding")))))
      val state = _ready(repair)
      val targetgeneration = state.generation - 1
      val dirty = Vector(GoalPhaseCandidateObservationContinuation.Dirty("M", "M", "src/second.scala", None))
      val current = _observation(state, _oid('f'), dirty)
      val proposed = state.plan.copy(revision = "initial-source-document-question", steps = state.plan.steps.updated(1,
        state.plan.steps(1).copy(dependencies = Vector.empty)))
      val assessment = R.assess(state, proposed, _plan.phaseOwnedPaths, Vector("first-test", "second-test"),
        Vector("phase-test"), current, state.candidate, false, Some(targetgeneration))
      val payload = _payload(state, proposed, assessment).copy(observation = current, sourceTargetGeneration = Some(targetgeneration))
      val waiting = _take(R.selectedStateC(state, payload))
      val reference = ContextReference("model-initial-source-keep", "1")
      When("the model's authenticated Keep tuple resolves the document ambiguity at target generation plus one")
      val kept = _take(R.selectedStateC(waiting, payload.copy(humanAnswer = Some(reference)),
        Some("KeepCurrentRequirements" -> reference)))
      Then("ordinary capture uses a later generation with the original requirements base prefix and charged repair history")
      targetgeneration should be >= 1L
      state.generation shouldBe targetgeneration + 1
      state.repairs should be > 0
      state.repairObservations should not be empty
      state.candidate shouldBe None
      state.closureIntent shouldBe None
      kept.plan shouldBe state.plan
      kept.stage shouldBe O.Stage.InspectCandidate
      kept.generation should be > (targetgeneration + 1)
      kept.base shouldBe state.base
      kept.completed shouldBe state.completed
      kept.repairs shouldBe state.repairs
      kept.ordinaryRepairs shouldBe state.ordinaryRepairs
      kept.pendingRepair shouldBe state.pendingRepair
      kept.repairControl shouldBe state.repairControl
      kept.repairObservations shouldBe state.repairObservations
      kept.candidate shouldBe None
      kept.checksReceipt shouldBe None
      kept.reviewEvidence shouldBe None

      Given("the resulting genuinely later-generation capture remains unstarted with the same stored current source")
      val laterassessment = R.assess(kept, proposed, _plan.phaseOwnedPaths, Vector("first-test", "second-test"),
        Vector("phase-test"), current, kept.candidate, false, Some(targetgeneration))
      val laterpayload = payload.copy(assessment = laterassessment)
      val laterwaiting = _take(R.selectedStateC(kept, laterpayload))
      When("the same model Keep tuple resolves the later document question without any candidate or effect")
      val later = _take(R.selectedStateC(laterwaiting, laterpayload.copy(humanAnswer = Some(reference)),
        Some("KeepCurrentRequirements" -> reference)))
      Then("the unstarted later generation remains unchanged without effects evidence or a repair refund")
      later.plan shouldBe kept.plan
      later.stage shouldBe O.Stage.InspectCandidate
      later.generation shouldBe kept.generation
      later.base shouldBe kept.base
      later.completed shouldBe kept.completed
      later.repairs shouldBe kept.repairs
      later.ordinaryRepairs shouldBe kept.ordinaryRepairs
      later.repairControl shouldBe kept.repairControl
      later.repairObservations shouldBe kept.repairObservations
      later.candidate shouldBe None
      later.checksReceipt shouldBe None
      later.reviewEvidence shouldBe None
      later.closureIntent shouldBe None
    }

    "reject missing crossed or unoffered actual Human choice and reference pairs" in {
      Given("one saved document question and its actual answer reference")
      val state = _ready(_first_closed)
      val assessment = R.Assessment(Vector.empty, Vector("Actual document meaning differs"), Vector.empty, false, false)
      val payload = _payload(state, state.plan, assessment)
      val reference = ContextReference("actual-answer", "1")
      val answered = payload.copy(humanAnswer = Some(reference))
      When("a caller supplies a reference without a choice or a choice without the matching genuine reference")
      val missing = R.selectedStateC(state, answered)
      val crossed = R.selectedStateC(state, answered, Some("KeepCurrentRequirements" -> reference.copy(revision = "2")))
      val absent = R.selectedStateC(state, payload, Some("AdoptDevelopmentReconstruction" -> reference))
      val deferred = R.selectedStateC(state, answered, Some("Defer" -> reference))
      Then("none can infer adoption or select a managed continuation")
      Vector(missing, crossed, absent, deferred).foreach(_.isSuccess shouldBe false)
    }

    "keep real unresolved effects and consumed repair allowance after resolving the document choice" in {
      Given("a consumed repair followed by a checked candidate and a still unresolved closure intent")
      val observed = _take(_ready(_initial).acceptC(O.CandidateObserved(O.Snapshot(_base.head, _oid('c')))))
      val repair = _take(observed.acceptC(O.RepairableChecksObserved(observed.candidate.get, Vector("first-test"),
        "actual-failure", O.RepairDirective("Repair actual source", Vector("Actual finding")))))
      val checked = _checked(repair, 'e')
      val reviewed = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
        "actual-check", "independent-current-review", O.ReviewOutcome.Accept)))
      val closing = _take(reviewed.acceptC(O.ClosureIntentSaved(reviewed.candidate.get, "actual-unknown-commit")))
      val assessment = _assessment(closing, closing.plan, _observation(closing, closing.candidate.get.tree),
        uncertainty = Vector("actual-unknown-commit is UNKNOWN; only the original owner may observe it"))
        .copy(questions = Vector("Proposed document meaning differs"), incompatible = Vector("Rejected proposed identity differs"))
      val payload = _payload(closing, closing.plan.copy(identity = "rejected-proposal"), assessment)
      val waiting = _take(R.selectedStateC(closing, payload))
      val reference = ContextReference("actual-keep-unknown", "1")
      When("the actual Keep answer resolves only the rejected projection")
      val kept = _take(R.selectedStateC(waiting, payload.copy(humanAnswer = Some(reference)),
        Some("KeepCurrentRequirements" -> reference)))
      Then("the original UNKNOWN remains a Decision without replay new generation or repair refund")
      kept.plan shouldBe closing.plan
      kept.stage shouldBe O.Stage.Decision
      kept.decision.get should include("actual-unknown-commit")
      kept.decision.get should not include "Rejected proposed identity"
      kept.closureIntent shouldBe closing.closureIntent
      kept.generation shouldBe closing.generation
      kept.repairs shouldBe closing.repairs
      kept.ordinaryRepairs shouldBe closing.ordinaryRepairs
      kept.pendingRepair shouldBe closing.pendingRepair
      kept.repairControl shouldBe closing.repairControl
      kept.repairObservations shouldBe closing.repairObservations
      kept.checksReceipt shouldBe closing.checksReceipt
      kept.reviewEvidence shouldBe closing.reviewEvidence
      R.choicesFor(payload) should not contain "AdoptDevelopmentReconstruction"
    }

    "preserve effective review lint code and retained observation triage and inventory obligations" in {
      Given("the original remaining Step policies and proposed weakenings of each effective requirement")
      val policy = GoalPhaseStepAssessment.Policy("required-policy", "code", "required", "off", "off", Vector.empty)
      val observation = GoalPhaseStepAssessment.Observation("original-observation", ContextReference("original-source", "1"),
        "Retain this actual concern", "immediate", "Unresolved original requirement", None)
      val inventory = GoalPhaseStepTriage.Inventory(ContextReference("original-inventory", "1"), true,
        Vector(GoalPhaseStepTriage.ScenarioMapping("operation", Vector("src/second.scala"), Vector("original-scenario"))))
      val cases = Vector(
        ("default independent review", None, Some(policy.copy(detailedReview = "conditional"))),
        ("explicit required review", Some(policy), Some(policy.copy(detailedReview = "conditional"))),
        ("required lint switched off", Some(policy.copy(scalaLint = "required")), Some(policy)),
        ("required lint policy removed", Some(policy.copy(scalaLint = "required")), None),
        ("code checks changed to documentation", Some(policy), Some(policy.copy(kind = "documentation"))),
        ("original observation removed", Some(policy.copy(observations = Vector(observation))), Some(policy)),
        ("original observation policy dropped", Some(policy.copy(observations = Vector(observation))), None),
        ("original observation redefined", Some(policy.copy(observations = Vector(observation))),
          Some(policy.copy(observations = Vector(observation.copy(reason = "Replacement concern"))))),
        ("original triage removed", Some(policy.copy(semanticTriage = "conditional")), Some(policy)),
        ("original triage policy dropped", Some(policy.copy(semanticTriage = "conditional")), None),
        ("original inventory dropped", Some(policy.copy(scenarioInventory = Some(inventory))), Some(policy)),
        ("original inventory policy dropped", Some(policy.copy(scenarioInventory = Some(inventory))), None),
        ("original inventory replaced", Some(policy.copy(scenarioInventory = Some(inventory))),
          Some(policy.copy(scenarioInventory = Some(inventory.copy(source = ContextReference("replacement", "1")))))))
      cases.foreach { (label, original, replacement) =>
        Given(label)
        val plan = _plan.copy(steps = _plan.steps.updated(0, _plan.steps.head.copy(actionPolicy = original)))
        val state = _ready(_take(O.startC(plan, _base, Some(GoalPhaseRuntimeRepairControl.version))))
        val proposed = plan.copy(revision = "weakened", steps = plan.steps.updated(0,
          plan.steps.head.copy(actionPolicy = replacement)))
        When("assessment compares the proposed policy and a crafted assessment attempts to bypass that comparison")
        val assessment = _assessment(state, proposed, _observation(state, _oid('c')))
        val payload = _payload(state, proposed, assessment)
        val reference = ContextReference("actual-policy-answer", "1")
        val forged = payload.copy(assessment = assessment.copy(incompatible = Vector.empty), humanAnswer = Some(reference))
        val bypass = R.selectedStateC(state, forged, Some("AdoptDevelopmentReconstruction" -> reference))
        Then("the weakening is incompatible cannot offer Adopt and cannot execute adoption even with a false assessment")
        withClue(label) {
          assessment.incompatible should contain("required original action policy obligations were removed or weakened")
          R.choicesFor(payload) should not contain "AdoptDevelopmentReconstruction"
          bypass.isSuccess shouldBe false
        }
      }
    }

    "allow genuine strengthening and normal default policy restoration when every old obligation survives" in {
      Given("conditional review without retained observations lint triage or inventory")
      val policy = GoalPhaseStepAssessment.Policy("conditional-policy", "code", "conditional", "off", "off", Vector.empty)
      val plan = _plan.copy(steps = _plan.steps.updated(0, _plan.steps.head.copy(actionPolicy = Some(policy))))
      val state = _ready(_take(O.startC(plan, _base, Some(GoalPhaseRuntimeRepairControl.version))))
      Vector(Some(policy.copy(detailedReview = "required", scalaLint = "required")), None).foreach { replacement =>
        Given("a complete proposal preserving the original checks and strengthening its review route")
        val proposed = plan.copy(revision = "strengthened", steps = plan.steps.updated(0,
          plan.steps.head.copy(actionPolicy = replacement)))
        val assessment = _assessment(state, proposed, _observation(state, _oid('c')))
        val payload = _payload(state, proposed, assessment)
        val reference = ContextReference("actual-strengthening-answer", "1")
        When("a genuine admitted Human answer selects the strengthened requirements")
        val next = _take(R.selectedStateC(state, payload.copy(humanAnswer = Some(reference)),
          Some("AdoptDevelopmentReconstruction" -> reference)))
        Then("the common route admits strengthening without losing checks prefix base or repair allowance")
        assessment.incompatible shouldBe empty
        R.choicesFor(payload) should contain("AdoptDevelopmentReconstruction")
        next.plan shouldBe proposed
        next.plan.steps.head.checks shouldBe plan.steps.head.checks
        next.base shouldBe state.base
        next.completed shouldBe state.completed
        next.repairs shouldBe state.repairs
      }
    }

    "retain original observations triage and inventory when a genuine clarification strengthens required review" in {
      Given("a conditional original policy with an exact retained observation triage and scenario inventory")
      val observation = GoalPhaseStepAssessment.Observation("original-concern", ContextReference("original-evidence", "1"),
        "Retain the original concern", "immediate", "Existing obligation", None)
      val inventory = GoalPhaseStepTriage.Inventory(ContextReference("retained-inventory", "1"), false, Vector.empty)
      val original = GoalPhaseStepAssessment.Policy("original-policy", "code", "conditional", "off", "off",
        Vector(observation), "conditional", Some(inventory))
      val plan = _plan.copy(steps = _plan.steps.updated(0, _plan.steps.head.copy(actionPolicy = Some(original))))
      val state = _ready(_take(O.startC(plan, _base, Some(GoalPhaseRuntimeRepairControl.version))))
      val strengthened = original.copy(detailedReview = "required", scalaLint = "required")
      val proposed = plan.copy(revision = "clarified", steps = plan.steps.updated(0,
        plan.steps.head.copy(actionPolicy = Some(strengthened))))
      val assessment = _assessment(state, proposed, _observation(state, _oid('c')))
      val reference = ContextReference("actual-clarification-answer", "1")
      When("the genuine admitted choice adopts the clarification with every old policy obligation present")
      val next = _take(R.selectedStateC(state, _payload(state, proposed, assessment).copy(humanAnswer = Some(reference)),
        Some("AdoptDevelopmentReconstruction" -> reference)))
      Then("the stronger policy retains the exact old observations triage inventory and required checks")
      assessment.incompatible shouldBe empty
      next.plan.steps.head.actionPolicy shouldBe Some(strengthened)
      strengthened.observations shouldBe original.observations
      strengthened.scenarioInventory shouldBe original.scenarioInventory
      strengthened.semanticTriage shouldBe original.semanticTriage
      next.plan.steps.head.checks shouldBe plan.steps.head.checks
      next.repairs shouldBe state.repairs
    }

    "retain the original convergence scope across a common Plan revision and clear it only at actual next-Step closure" in {
      Given("three recurring actual-shaped failures in one Step with an admitted limit of ten")
      val plan = _plan.copy(repairLimit = 10)
      var state = _take(O.startC(plan, _base, Some(GoalPhaseRuntimeRepairControl.version)))
      (1 to 3).foreach { index =>
        val observed = _take(_ready(state).acceptC(O.CandidateObserved(O.Snapshot(_base.head, _oid('c')))))
        state = _take(observed.acceptC(O.RepairableChecksObserved(observed.candidate.get, Vector("first-test"),
          "recurring-check-" + index, O.RepairDirective("Correct recurring failure", Vector("Same actual finding")))))
      }
      val ready = _ready(state)
      val proposed = plan.copy(revision = "current-projection")
      When("common reconstruction selects the compatible current revision without rewriting any observation")
      val selected = _take(ready.reconstructDevelopmentC(proposed, true, None))
      val observed = _take(selected.acceptC(O.CandidateObserved(O.Snapshot(_base.head, _oid('e')))))
      val stopped = _take(observed.acceptC(O.RepairableChecksObserved(observed.candidate.get, Vector("first-test"),
        "recurring-check-4", O.RepairDirective("Correct recurring failure", Vector("Same actual finding")))))
      Then("the fourth recurrence keeps its original scope and convergence stop instead of opening another repair batch")
      selected.repairObservations shouldBe ready.repairObservations
      selected.repairs shouldBe ready.repairs
      selected.ordinaryRepairs shouldBe ready.ordinaryRepairs
      selected.repairControl shouldBe ready.repairControl
      stopped.stage shouldBe O.Stage.Decision
      stopped.repairs shouldBe selected.repairs
      stopped.repairObservations.map(_.scope).distinct shouldBe ready.repairObservations.map(_.scope).distinct
      stopped.repairControl.flatMap(_.stop).get should include("recurrence")

      When("the same selected current source instead passes review and a real-shaped commit advances the Step")
      val checked = _take(observed.acceptC(O.ChecksObserved(observed.candidate.get, Vector("first-test"), "current-passed", true)))
      val accepted = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
        "current-passed", "current-independent-review", O.ReviewOutcome.Accept)))
      val intent = _take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "current-first-close")))
      val next = _take(intent.acceptC(O.StepCommitObserved("current-first-close", O.Commit(_oid('d'), _base.head, _oid('e')))))
      val nextcandidate = _take(_ready(next).acceptC(O.CandidateObserved(O.Snapshot(_oid('d'), _oid('f')))))
      val nextfailure = _take(nextcandidate.acceptC(O.RepairableChecksObserved(nextcandidate.candidate.get,
        Vector("second-test"), "new-step-failure", O.RepairDirective("Different Step failure", Vector("New actual finding")))))
      Then("normal Step closure clears the ephemeral override and the next Step uses its current Plan revision")
      next.repairObservations shouldBe empty
      next.repairs shouldBe 0
      nextfailure.repairObservations.map(_.scope) shouldBe Vector(Json.arr(Json.fromString(plan.identity),
        Json.fromString(proposed.revision), Json.fromString("Step"), Json.fromString("second")).noSpaces)
    }

    "retain exact applicable candidate check and review evidence at an unchanged selected source" in {
      Given("an actual-shaped checked current Step and matching HEAD/index with a clean selected worktree")
      val state = _checked(_first_closed, 'e')
      val assessment = _assessment(state, state.plan, _observation(state, state.candidate.get.tree))
      When("the native owner selects reconstruction without affected evidence")
      val next = _take(R.selectedStateC(state, _payload(state, state.plan, assessment)))
      Then("the same generation, accepted prefix, candidate and check receipt remain available")
      assessment.retainEvidence shouldBe true
      assessment.freshCapture shouldBe false
      next.generation shouldBe state.generation
      next.stage shouldBe state.stage
      next.candidate shouldBe state.candidate
      next.checksReceipt shouldBe state.checksReceipt
      next.reviewEvidence shouldBe state.reviewEvidence
      next.completed shouldBe state.completed
    }

    "preserve completed commits base and consumed repair history when current source needs a fresh candidate" in {
      Given("one closed Step and one consumed ordinary repair followed by accepted corrective work")
      val inspected = _take(_ready(_first_closed).acceptC(O.CandidateObserved(O.Snapshot(_oid('d'), _oid('e')))))
      val repair = _take(inspected.acceptC(O.RepairableChecksObserved(inspected.candidate.get, Vector("second-test"),
        "failed-second-test", O.RepairDirective("Correct actual failure", Vector("Actual missing behavior")))))
      val state = _ready(repair)
      When("the common owner selects the current source after accepted repair")
      val next = _take(state.reconstructDevelopmentC(state.plan, true, Some(state.generation - 1)))
      Then("fresh inspection uses one later generation with the original accepted prefix and repair reservation history")
      next.stage shouldBe O.Stage.InspectCandidate
      next.generation shouldBe state.generation + 1
      next.base shouldBe state.base
      next.completed shouldBe state.completed
      next.repairs shouldBe state.repairs
      next.ordinaryRepairs shouldBe state.ordinaryRepairs
      next.repairControl shouldBe state.repairControl
      next.repairObservations shouldBe state.repairObservations
      next.repairDirective shouldBe state.repairDirective
      next.pendingRepair shouldBe state.pendingRepair
      next.candidate shouldBe None
      next.checksReceipt shouldBe None
    }

    "retain an unstarted reconstructed capture without claiming unchanged worktree bytes" in {
      Given("a common reconstruction with no candidate/check/closure intent and more unstaged content")
      val state = _take(_ready(_first_closed).reconstructDevelopmentC(_plan, true, None))
      val dirty = Vector(GoalPhaseCandidateObservationContinuation.Dirty("M", "M", "src/second.scala", None))
      When("the same effective projection is assessed before capture has begun")
      val assessment = _assessment(state, state.plan, _observation(state, _oid('e'), dirty), unstarted = true)
      val next = _take(R.selectedStateC(state, _payload(state, state.plan, assessment)))
      Then("normal capture remains unstarted at the same generation and no byte equality is asserted")
      assessment.freshCapture shouldBe false
      assessment.retainEvidence shouldBe false
      next.generation shouldBe state.generation
      next.stage shouldBe O.Stage.InspectCandidate
      next.repairs shouldBe state.repairs
    }

    "admit compatible remaining purpose refresh while retaining original obligations" in {
      Given("a completed prefix and a current complete projection changing only the remaining purpose and revision")
      val state = _ready(_first_closed)
      val proposed = state.plan.copy(revision = "current", steps = state.plan.steps.updated(1,
        state.plan.steps(1).copy(purpose = "Complete the actual current development boundary")))
      When("current authority and actual source observation are compared")
      val assessment = _assessment(state, proposed, _observation(state, _oid('e')))
      val next = _take(R.selectedStateC(state, _payload(state, proposed, assessment)))
      Then("the new current projection keeps completed Step identity/order and required checks without another allowance")
      assessment.reasons shouldBe empty
      next.plan shouldBe proposed
      next.completed shouldBe state.completed
      next.repairs shouldBe state.repairs
      next.generation shouldBe state.generation + 1
    }

    "round-trip captured explicit usage with actual-shaped dirty source and absent optional reconstruction references" in {
      Given("completed current semantic work and a raw Plan adopting explicit usage without acceptance conditions")
      val state = _ready(_first_closed)
      val rawplan = state.plan.copy(acceptance = Some(GoalPhaseAcceptanceBoundary.Adoption(
        "SPECIFICATION-OWNED current usage", Vector.empty)), effectiveAcceptance = None)
      val capturedplan = _take(GoalPhaseAcceptanceBoundary.captureC(None, rawplan))
      val original = _payload(state, capturedplan, R.Assessment(Vector.empty,
        Vector("current/proposed Usage Contract or acceptance conditions differ"), Vector.empty, true, false))
      val dirty = Vector(
        GoalPhaseCandidateObservationContinuation.Dirty(" ", "M", "src/second.scala", None),
        GoalPhaseCandidateObservationContinuation.Dirty("?", "?", "docs/checklist.md", None))
      val payload = original.copy(componentInstance = "org.simplemodeling.textus.SmWorkflow@default", fromRevision = "8",
        observation = original.observation.copy(dirty = dirty), sourceTargetGeneration = None)
      When("the exact common reconstruction codec encodes and decodes the captured business payload")
      val encoded = R.codec.encode(payload)
      val decoded = R.codec.decode(encoded)
      Then("explicit captured usage empty effective conditions dirty entries and optional absence survive unchanged")
      withClue("Captured explicit-usage reconstruction codec result: " + decoded + "; encoded=" + encoded.noSpaces) {
        R.valid(payload) shouldBe true
        decoded shouldBe Right(payload)
      }
      val restored = decoded.toOption.get
      encoded.hcursor.downField("payload").downField("observation").downField("dirty").downArray
        .downField("renamedFrom").focus shouldBe Some(Json.Null)
      restored.documents.captured.proposal.plan shouldBe rawplan
      restored.documents.captured.plan.effectiveAcceptance shouldBe Some(GoalPhaseAcceptanceBoundary.Effective(
        Some("SPECIFICATION-OWNED current usage"), Vector.empty))
      restored.observation.dirty shouldBe dirty
      restored.sourceTargetGeneration shouldBe None
      restored.semanticResult shouldBe Some(ContextReference("retained-result", "1"))
      Vector("sourceTargetGeneration", "candidateReference", "validationReference", "previousSelection", "humanAnswer").foreach { key =>
        encoded.hcursor.downField("payload").downField(key).focus shouldBe None
      }
    }

    "restore only omitted nullable dirty origins in public Human context while retaining strict stored reconstruction wire" in {
      Given("captured explicit usage with one nullable dirty origin and one literal renamed source path")
      val state = _ready(_first_closed)
      val rawplan = state.plan.copy(acceptance = Some(GoalPhaseAcceptanceBoundary.Adoption(
        "SPECIFICATION-OWNED current usage", Vector.empty)), effectiveAcceptance = None)
      val capturedplan = _take(GoalPhaseAcceptanceBoundary.captureC(None, rawplan))
      val original = _payload(state, capturedplan, R.Assessment(Vector.empty,
        Vector("current/proposed Usage Contract or acceptance conditions differ"), Vector.empty, true, false))
      val dirty = Vector(
        GoalPhaseCandidateObservationContinuation.Dirty(" ", "M", "src/second.scala", None),
        GoalPhaseCandidateObservationContinuation.Dirty("R", " ", "src/current.scala", Some("src/previous.scala")))
      val payload = original.copy(observation = original.observation.copy(dirty = dirty))
      When("the existing Record presentation projects the encoded context and native metadata restores public property names")
      val encoded = R.codec.encode(payload)
      val projected = io.circe.parser.parse(_take(GoalPhaseNativeMetadata.record(encoded).toJsonStringC))
        .fold(error => fail("Public reconstruction JSON failed: " + error.message), identity)
      val normalized = GoalPhaseNativeMetadata.normalizePublicJson(projected)
        .fold(reason => fail("Public reconstruction normalization failed: " + reason), identity)
      val publicresult = R.decodeContext(normalized)
      val nativeresult = R.decodeContext(encoded)
      Then("only the public nullable omission is restored and both contexts retain the exact typed payload and literal renamed origin")
      withClue("Public reconstruction context result: " + publicresult + "; normalized=" + normalized.noSpaces) {
        publicresult shouldBe Right(payload)
      }
      nativeresult shouldBe Right(payload)
      publicresult.toOption.get.observation.dirty shouldBe dirty
      normalized.hcursor.downField("payload").downField("observation").downField("dirty").downArray
        .downField("renamedFrom").focus shouldBe None
      normalized.hcursor.downField("payload").downField("observation").downField("dirty").downArray.right
        .get[String]("renamedFrom") shouldBe Right("src/previous.scala")

      Given("the same canonical context with an omitted stored origin or deliberately malformed public fields")
      val omitted = encoded.hcursor.downField("payload").downField("observation").downField("dirty").downArray
        .withFocus(_.mapObject(_.remove("renamedFrom"))).top.get
      val unknown = encoded.hcursor.downField("payload").downField("observation").downField("dirty").downArray
        .withFocus(_.mapObject(_.add("unknownDirtyField", Json.True))).top.get
      val invalid = encoded.hcursor.downField("payload").downField("observation").downField("dirty").downArray
        .withFocus(_.mapObject(_.add("renamedFrom", Json.fromInt(7)))).top.get
      val crossed = encoded.mapObject(_.add("schemaVersion", Json.fromString(R.schema + ":crossed")))
      When("the durable codec reads an omitted origin and the context boundary reads unknown invalid null or crossed inputs")
      val storedresult = R.codec.decode(omitted)
      val omittedcontext = R.decodeContext(omitted)
      val refused = Vector(unknown, invalid, Json.Null, crossed).map(R.decodeContext)
      Then("stored canonical requirements remain strict while public restoration grants no other field or schema relaxation")
      storedresult.isLeft shouldBe true
      omittedcontext shouldBe Right(payload)
      refused.foreach(_.isLeft shouldBe true)
      R.codec.decode(encoded) shouldBe Right(payload)
    }

    "offer adoption only for resolvable document meaning within the existing authority" in {
      Given("a live current projection with a concrete Usage Contract ambiguity and a separate unresolved commit")
      val state = _ready(_first_closed)
      val ordinary = R.Assessment(Vector.empty, Vector("Usage Contract changed"), Vector.empty, true, false)
      val documents = _payload(state, state.plan, ordinary)
      val unresolved = documents.copy(assessment = ordinary.copy(uncertainEffects = Vector(
        "Commit intent old-close is UNKNOWN; collect its actual result through the closure owner")))
      val crossed = documents.copy(assessment = ordinary.copy(incompatible = Vector("accepted completed Step diverged")))
      When("the actual applicable ordered Human options are selected")
      val choices = Vector(documents, unresolved, crossed).map(R.choicesFor)
      Then("only the resolvable semantic question offers Adopt and every reason/action is explicit")
      choices.head shouldBe R.choices
      choices.tail.foreach(_ shouldBe Vector("KeepCurrentRequirements", "Defer", "Cancel"))
      R.notice(unresolved, state.plan) should include("closure owner")
      R.notice(unresolved, state.plan) should include("without rollback")
      R.codec.decode(R.codec.encode(documents)) shouldBe Right(documents)
      documents.humanAnswer shouldBe None
    }

    "preserve exhausted repair and unresolved closure boundaries instead of reopening implementation" in {
      Given("an exhausted genuine repair and an independent checked closure intent")
      val plan = _plan.copy(repairLimit = 0)
      val ready = _ready(_take(O.startC(plan, _base, Some(GoalPhaseRuntimeRepairControl.version))))
      val seen = _take(ready.acceptC(O.CandidateObserved(O.Snapshot(_base.head, _oid('c')))))
      val stopped = _take(seen.acceptC(O.RepairableChecksObserved(seen.candidate.get, Vector("first-test"),
        "failed-without-budget", O.RepairDirective("Actual failure", Vector("Fix the actual failure")))))
      val checked = _checked(_initial, 'c')
      val accepted = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
        "actual-check", "actual-review", O.ReviewOutcome.Accept)))
      val closing = _take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "unknown-commit")))
      When("reconstruction retains each original pending action")
      val next = _take(stopped.reconstructDevelopmentC(stopped.plan, true, None))
      val uncertain = _take(closing.reconstructDevelopmentC(closing.plan, false, None,
        Vector("unknown-commit requires existing closure observation; do not replay")))
      Then("no implementation restart, repair refund, inferred commit or new allowance is produced")
      next.stage shouldBe O.Stage.Decision
      next.generation shouldBe stopped.generation
      next.repairs shouldBe stopped.repairs
      next.pendingRepair shouldBe stopped.pendingRepair
      next.repairControl shouldBe stopped.repairControl
      uncertain.stage shouldBe O.Stage.Decision
      uncertain.closureIntent shouldBe Some("unknown-commit")
      uncertain.checksReceipt shouldBe closing.checksReceipt
      uncertain.reviewEvidence shouldBe closing.reviewEvidence
    }

    "retain the owning checklist grammar and require no new fence for prose documents" in {
      Given("actual prose with a checked item, a continued acceptance line and an ignored fenced example")
      val phase = "# Current development\nUse current source and retain native results.\n"
      val checklist = "- [x] actual: Required current behavior\n  and retained obligations\n\n```text\n- [ ] example: ignored\n```\n"
      When("the existing document owner extracts its actual checklist items")
      val items = _take(GoalPhaseDocumentPlan.checklistItemsC(checklist))
      Then("checked means only document data and the same parser rejects duplicate identities")
      _take(GoalPhaseDocumentPlan.selectedPlanPresentC(phase)) shouldBe false
      items shouldBe Vector(GoalPhaseDocumentPlan.Item("actual", "Required current behavior and retained obligations", true))
      GoalPhaseDocumentPlan.checklistItemsC(checklist + "- [ ] actual: Duplicate\n").isSuccess shouldBe false
    }

    "observe an already indexed selected deletion and restage actual unstaged or untracked content" in {
      Given("typed Git status for staged deletion, indexed content and later worktree edits")
      val staged = Vector(RepositorySyncManagedGitObservation.DirtyPath('D', ' ', "src/old.scala", None))
      val indexed = Vector(RepositorySyncManagedGitObservation.DirtyPath('M', ' ', "src/new.scala", None))
      val unstaged = Vector(RepositorySyncManagedGitObservation.DirtyPath('M', 'M', "src/new.scala", None))
      val untracked = Vector(RepositorySyncManagedGitObservation.DirtyPath('?', '?', "src/new.scala", None))
      When("ordinary candidate construction decides whether selected staging has remaining work")
      val result = Vector(staged, indexed, unstaged, untracked).map(GoalPhaseNativeCandidate.alreadyIndexed("src", _))
      Then("fresh observations reuse already indexed contents and actual changed contents still require normal staging")
      result shouldBe Vector(true, true, false, false)
    }

    "keep a closed Phase terminal and reject divergence from the accepted prefix" in {
      Given("the actual accepted first commit and a projection that replaces that completed Step")
      val state = _first_closed
      val proposed = state.plan.copy(steps = state.plan.steps.updated(0, state.plan.steps.head.copy(identity = "replacement")))
      When("the common reconstruction controller is asked to adopt the divergent projection")
      val result = state.reconstructDevelopmentC(proposed, true, None)
      Then("the accepted prefix cannot be changed by reconstruction or a generation increment")
      result.isSuccess shouldBe false
      state.completed.map(_.step) shouldBe Vector("first")
      val checked = _checked(state, 'e')
      val accepted = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
        "actual-check", "second-review", O.ReviewOutcome.Accept)))
      val intent = _take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "second-close")))
      val all = _take(intent.acceptC(O.StepCommitObserved("second-close", O.Commit(_oid('f'), _oid('d'), _oid('e')))))
      val phase = _take(all.acceptC(O.CandidateObserved(all.tipSnapshot)))
      val validated = _take(phase.acceptC(O.ChecksObserved(all.tipSnapshot, _plan.phaseChecks, "phase-check", true)))
      val phaseaccepted = _take(validated.acceptC(O.ReviewAdmitted(_key(validated), all.tipSnapshot,
        "phase-check", "phase-review", O.ReviewOutcome.Accept)))
      val closed = _take(phaseaccepted.acceptC(O.PhaseLocallyCompleted(all.tipSnapshot, all.completed.map(_.commit), "phase-review")))
      closed.stage shouldBe O.Stage.Closed
      closed.reconstructDevelopmentC(closed.plan, true, None).isSuccess shouldBe false
    }
  }
}

/** Inspection-subject consumption is derived native scoping, not effect or
  * Human authority. This leaf is independently selectable without prior cases.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.AdmissionTestDeclaration])
final class GoalPhaseCurrentHistoryRecoverySubjectSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  "Current native source-history subjects" should {
    "retain consumed authority while allowing a different inspection to seek its own observation or Human" in {
      Given("one already consumed actual inspection subject and a distinct later subject in the same Session")
      val original = ContextReference("same-native-session", "25")
      val current = ContextReference("same-native-session", "39")
      val consumed = Set(original)
      val records = Vector(original)
      val plan = O.Plan("subject-plan", "1", "phase", "repository",
        Vector(O.Step("step", "Observe the current source", Vector(sourcePath), Vector.empty, Vector("test"))), Vector("test"))
      val state = SplitPhaseExplicitPartitionFixture.take(O.startC(plan, O.Snapshot("a" * 40, "b" * 40)))
      When("the pure scoping helper evaluates the original and later native subjects with legacy Boolean retained or reset")
      val old = Vector(false, true).map(value => R.sourceHistoryConsumed(value, consumed, original))
      val next = R.sourceHistoryConsumed(true, consumed, current)
      Then("the old subject stays blocked and the different subject is eligible only for its existing public admission")
      old shouldBe Vector(true, true)
      next shouldBe false
      R.sourceHistoryConsumed(false, Set.empty, current) shouldBe false
      consumed shouldBe Set(original)
      records shouldBe Vector(original)
      state.plan shouldBe plan
      state.generation shouldBe 1L
      state.repairs shouldBe 0
      state.ordinaryRepairs shouldBe 0
      state.completed shouldBe empty
      state.pendingRepair shouldBe None
    }

    "fail closed for missing legacy provenance crossed Sessions and malformed native references" in {
      Given("a legacy consumed flag without reconstructible target and several inadmissible context references")
      val original = ContextReference("same-native-session", "25")
      val current = original.copy(revision = "39")
      val invalid = Vector(null, current.copy(identity = ""), current.copy(identity = " same-native-session"),
        current.copy(revision = "0"), current.copy(revision = "-1"), current.copy(revision = "+39"),
        current.copy(revision = "039"), current.copy(revision = "9223372036854775808"), current.copy(revision = null))
      When("legacy provenance current references and consumed references are scoped")
      val missing = R.sourceHistoryConsumed(true, Set.empty, current)
      val crossed = R.sourceHistoryConsumed(true, Set(original), current.copy(identity = "other-session"))
      Then("none permits a new recovery boundary and no missing identity is guessed or reset")
      missing shouldBe true
      crossed shouldBe true
      R.sourceHistoryConsumed(false, null, current) shouldBe true
      invalid.foreach(reference => R.sourceHistoryConsumed(false, Set.empty, reference) shouldBe true)
      invalid.foreach(reference => R.sourceHistoryConsumed(false, Set(reference), current) shouldBe true)
    }

    "interpret the actual multi-class installed specification source with one managed goldenport header" in {
      Given("the real repository specification source whose additional classes use ordinary dated labels")
      val path = "src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalDevelopmentReconstructionCliSpec.scala"
      val text = java.nio.file.Files.readString(java.nio.file.Path.of(path), java.nio.charset.StandardCharsets.UTF_8)
      val selected = SplitPhaseExplicitPartitionFixture.take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey)))
      When("the existing goldenport rule interprets the whole current file for its Nov6 source date")
      val interpreted = SplitPhaseExplicitPartitionFixture.take(WorkflowSourceHistoryRule.applyC(selected,
        WorkflowSourceHistoryRule.Input(path, text, java.time.LocalDate.of(2026, 11, 6), None, None, true)))
      Then("the real parser accepts one managed header and preserves the additional class descriptions and dates")
      interpreted.outcome shouldBe "unchanged"
      interpreted.text shouldBe text
      interpreted.providerIdentity shouldBe selected.identity
      interpreted.text should include("Created: Nov.  6, 2026")
      interpreted.text should include("Last revised: Nov.  6, 2026")
      interpreted.text should include("final class NativeGoalCurrentSourceHistoryStopCliSpec")
    }
  }
}
