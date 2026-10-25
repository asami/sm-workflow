package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionQuestionWire
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDependencyScope as D
import GoalPhaseNativeWork as W
import GoalPhaseOrderedHumanDecision as H
import GoalPhaseOrderedProgression as O
import GoalPhaseFindingRecording as R
import NativeExternalEvidence.Purpose
import NativeHumanEvidenceFixture as F

/** Specification-owned questions and references exercise contracts only.
  * These examples establish neither retained native consent nor effect authority.
  * @since   Oct. 19, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDependencyDecisionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[T](value: Consequence[T]): T = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Dependency decision contract failed: $failure")
  }
  private val _proposal = D.Proposal("specification-dependency", "1", "IMPLEMENTATION",
    D.Component("org.simplemodeling.workflow", "repository", "workflow-code"),
    D.Component("org.simplemodeling.contract", "repository", "workflow-code"),
    ContextReference("usage-contract:selected", "4"), "SPECIFICATION-OWNED linked dependency request",
    Vector("src/contract.scala", "src/contract-spec.scala"), Vector("Keep existing callers", "Expose selected behavior"),
    Vector(ContextReference("dependent-work:selected", "1")), Vector.empty)
  private val _reviewed = _proposal.copy(discovery = "REVIEW", discoveredEdits = Vector(_proposal.paths.head))
  private val _observations = R.Observations(Vector(R.Observation("observation-1", "HYGIENE",
    "SPECIFICATION-OWNED nonblocking observation", "Record separately without changing dependency authority")))
  private val _choices = Vector("ApproveDependencyScope", "RejectDependencyScope", "Defer", "Cancel")
  private val _answerreference = ContextReference("specification-owned-answer", "7")
  private def _result(value: D.Proposal = _proposal): W.Result =
    W.Result("decision-needed", "SPECIFICATION-OWNED dependency direction requested", Vector.empty,
      dependencyProposal = Some(value))
  private def _field(value: Json, key: String, selected: Json): Json = value.mapObject(_.add(key, selected))
  private def _payload(value: Json)(change: Json => Json): Json =
    value.mapObject(fields => fields.add("payload", change(fields("payload").get)))
  private def _question(value: D.Proposal = _proposal, observe: Boolean = false): WorkflowInteraction[H.Input, Nothing] = {
    val original = F.goalQuestion.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]]
    val work = F.work.copy(kind = if (value.discovery == "REVIEW") "ReviewStep" else "ImplementStep")
    val input = H.Input(work, ContextReference("specification-requesting-result", "3"),
      _result(value).copy(reviewObservations = Option.when(observe)(_observations)),
      "SPECIFICATION-OWNED question reason", Some(GoalPhaseRepairProgress.Snapshot(0, 0, 2)))
    F.goalQuestion.copy(current = original.copy(request = original.request.copy(input = Some(TypedValue(H.inputCodec.typeIdentity, input))),
      presentation = MinimalPresentation("Specification dependency question", Json.obj(
        "choices" -> Json.fromValues(_choices.map(Json.fromString))).noSpaces)))
  }
  private def _input(question: WorkflowInteraction[H.Input, Nothing]): H.Input =
    question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.input.get.value
  private def _target(question: WorkflowInteraction[H.Input, Nothing]): Json =
    _take(NativeHumanDecisionScope.questionBindingC(question, _take(ThreeWorkflowExternalEvidencePorts.goalHumanSourceC(question))))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _reviewstate: O.State = {
    val initial = _take(O.startC(F.plan, F.snapshot))
    val implemented = _take(initial.acceptC(O.SemanticCompleted(_key(initial), "SPECIFICATION-OWNED semantic result")))
    val observed = _take(implemented.acceptC(O.CandidateObserved(F.snapshot)))
    _take(observed.acceptC(O.ChecksObserved(F.snapshot, F.plan.steps.head.checks, "specification-check", true)))
  }
  private def _phasereviewstate: O.State = {
    val review = _reviewstate
    val accepted = _take(review.acceptC(O.ReviewAdmitted(_key(review), F.snapshot, "specification-check",
      "specification-review", O.ReviewOutcome.Accept)))
    val closing = _take(accepted.acceptC(O.ClosureIntentSaved(F.snapshot, "specification-intent")))
    val commit = O.Commit("c" * 40, F.snapshot.head, F.snapshot.tree)
    val closed = _take(closing.acceptC(O.StepCommitObserved("specification-intent", commit)))
    val snapshot = O.Snapshot(commit.identity, commit.tree)
    val observed = _take(closed.acceptC(O.CandidateObserved(snapshot)))
    _take(observed.acceptC(O.ChecksObserved(snapshot, F.plan.phaseChecks, "specification-phase-check", true)))
  }

  "Dependency source admission" should afterWord("in spec:goal-phase-dependency-scope, rules:R3,R6") {
    "admit matching implementation repair and independent review decisions while rejecting completion acceptance and mixed proposals" in {
      Given("actual NativeWork types and pure assigned review frontiers with only specification-owned reports")
      val repair = F.work.copy(kind = "RepairStep", repairDirective = Some(O.RepairDirective("Assigned finding", Vector("Finding"))),
        review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "Specification-owned diff", Vector.empty)))
      val revision = GoalPhasePlanRevision.Proposal("revision", "1", "Specification-owned Plan gap",
        F.plan.copy(revision = "2"))
      val review = _reviewstate
      val phase = _phasereviewstate
      When("the existing semantic and independent review owners consume matching discovery and decision-needed outcomes")
      val semantic = Vector(W.admitInitialResultC(F.work, _result()),
        W.admitInitialResultC(F.work, _result(_proposal.copy(discovery = "PLANNING"))),
        W.admitSemanticResultC(repair, _result(_proposal.copy(discovery = "REPAIR"))))
      val reviews = Vector(review, phase).map(state => GoalPhaseNativeReview.completedStateC(
        _result(_reviewed), "specification-review-result", state))
      val invalid = Vector(_result().copy(outcome = "completed"), _result().copy(dependencyProposal = null),
        _result().copy(dependencyProposal = Some(null)), _result(_reviewed), _result().copy(planRevision = Some(revision)))
        .map(value => W.admitInitialResultC(F.work, value))
      val invalidrepair = Vector(_result(), _result(_reviewed), _result(_proposal.copy(discovery = "REPAIR")).copy(outcome = "completed"))
        .map(value => W.admitSemanticResultC(repair, value))
      val invalidreview = Vector(review, phase).flatMap(state => Vector(
        _result(_reviewed).copy(outcome = "accept"), _result(_reviewed).copy(outcome = "repair", findings = Vector("Finding")),
        _result(), _result(_reviewed).copy(planRevision = Some(revision)))
        .map(value => GoalPhaseNativeReview.completedStateC(value, "specification-review-result", state)))
      Then("only matching decision-needed reports pass and review retains its read-only semantic source")
      semantic.foreach(_ shouldBe Consequence.unit)
      reviews.zip(Vector(review, phase)).foreach { (value, original) =>
        val paused = _take(value)
        paused.stage shouldBe O.Stage.Decision
        paused.decisionSource shouldBe Some(original.stage)
        paused.plan shouldBe original.plan
        paused.base shouldBe original.base
        paused.completed shouldBe original.completed
        GoalPhaseRepairProgress.snapshot(paused) shouldBe GoalPhaseRepairProgress.snapshot(original)
      }
      (invalid ++ invalidrepair).foreach(_.isSuccess shouldBe false)
      invalidreview.foreach(_.isSuccess shouldBe false)
    }

    "retain optional proposals through strict result codecs and refuse present null malformed and mixed proposal kinds" in {
      Given("one exact dependency result, an ordinary absent result and a complete Plan revision")
      val value = _result()
      val ordinary = value.copy(dependencyProposal = None)
      val wire = W.resultCodec.encode(value)
      val revision = GoalPhasePlanRevision.Proposal("revision", "1", "Specification Plan gap", F.plan.copy(revision = "2"))
      When("owning result decoding sees exact presence, omission and malformed presence")
      val refused = Vector(Json.Null, Json.True, Json.obj(), _field(_proposal.asJson, "paths", Json.Null))
        .map(selected => W.resultCodec.decode(_payload(wire)(_field(_, "dependencyProposal", selected)))) :+
        W.resultCodec.decode(W.resultCodec.encode(value.copy(planRevision = Some(revision))))
      Then("the proposal survives exactly, absence is omitted and every malformed or mixed presence fails")
      W.resultCodec.decode(wire) shouldBe Right(value)
      W.resultCodec.decode(W.resultCodec.encode(ordinary)) shouldBe Right(ordinary)
      W.resultCodec.encode(ordinary).hcursor.downField("payload").downField("dependencyProposal").focus shouldBe None
      refused.foreach(_.isLeft shouldBe true)
    }
  }

  "Saved dependency question and direction projection" should afterWord("in spec:goal-phase-dependency-scope, rules:R6,R7") {
    "retain exact saved business input and require an actual reference for either explicit dependency direction" in {
      Given("a specification-owned saved question and explicit fixture reference, without any native consent claim")
      val issued = _question()
      val savedinput = _input(issued)
      When("the owning Human input codec and issued-choice selector consume that full question")
      val saved = H.inputCodec.decode(H.inputCodec.encode(savedinput))
      Then("the requesting source, exact proposal, reason and counts survive with only dependency choices")
      saved shouldBe Right(savedinput)
      saved.toOption.get.result.dependencyProposal shouldBe Some(_proposal)
      saved.toOption.get.requestingResult shouldBe savedinput.requestingResult
      saved.toOption.get.work shouldBe savedinput.work
      saved.toOption.get.reason shouldBe savedinput.reason
      saved.toOption.get.repairProgress shouldBe savedinput.repairProgress
      _take(H.issuedChoicesC(issued)) shouldBe _choices
      _choices should not contain "ResumeAssignedWork"
      H.Installation(null).validateC.isSuccess shouldBe false

      Given("explicit approval and rejection contexts carrying the same exact proposal and specification-owned answer reference")
      Vector("ApproveDependencyScope", "RejectDependencyScope").foreach { choice =>
        val context = W.DecisionContext(savedinput.result, choice, "SPECIFICATION-OWNED rationale", Some(_answerreference))
        val request = savedinput.work.copy(decisionContext = Some(context))
        val wire = W.inputCodec.encode(request)
        When("the actual NativeWork input codec and public work projection consume the context")
        val restored = W.inputCodec.decode(wire)
        val publiccontext = NativeWorkflowOperationOutput.goalWork(request).decisionContext.get
        Then("the full requesting result and answer reference survive without modifying Plan base or owned paths")
        restored shouldBe Right(request)
        publiccontext.choice shouldBe choice
        publiccontext.decisionReference shouldBe Some(V.WorkflowArtifactReference(_answerreference.identity, _answerreference.revision))
        _take(D.fromPublicProposalC(publiccontext.requestingResult.dependencyProposal)) shouldBe Some(_proposal)
        restored.toOption.get.selectedPlan shouldBe savedinput.work.selectedPlan
        restored.toOption.get.originalBase shouldBe savedinput.work.originalBase
        val malformed = Vector(Json.Null, Json.True, Json.obj(), Json.obj("identity" -> Json.fromString("answer")),
          Json.obj("identity" -> Json.Null, "revision" -> Json.fromString("1")))
        malformed.foreach { selected =>
          W.inputCodec.decode(_payload(wire)(value => value.mapObject(fields => fields.add("decisionContext",
            _field(fields("decisionContext").get, "decisionReference", selected))))).isLeft shouldBe true
        }
        W.inputCodec.decode(W.inputCodec.encode(request.copy(decisionContext = Some(context.copy(decisionReference = None))))).isLeft shouldBe true
        W.inputCodec.decode(W.inputCodec.encode(request.copy(decisionContext = Some(context.copy(
          requestingResult = savedinput.result.copy(dependencyProposal = None)))))).isLeft shouldBe true
      }

      Given("an ordinary preexisting context without an answer reference")
      val ordinary = savedinput.work.copy(decisionContext = Some(W.DecisionContext(
        savedinput.result.copy(dependencyProposal = None), "ResumeAssignedWork", "Ordinary retained context")))
      When("the current codec reads its ordinary omitted-reference form")
      val ordinarywire = W.inputCodec.encode(ordinary)
      Then("ordinary compatibility remains while no absent reference becomes dependency direction")
      W.inputCodec.decode(ordinarywire) shouldBe Right(ordinary)
      ordinarywire.hcursor.downField("payload").downField("decisionContext").downField("decisionReference").focus shouldBe None
    }
  }

  "Independent dependency evidence targets" should afterWord("in spec:goal-phase-dependency-scope, rules:R6,R7") {
    "retain both observations and the complete proposal in one Human source and distinguish every bounded selection change" in {
      Given("a specification-issued REVIEW question with both observations and an exact dependency report")
      val issued = _question(_reviewed, observe = true)
      val source = _take(ThreeWorkflowExternalEvidencePorts.goalHumanSourceC(issued))
      val actual = _target(issued)
      val selected = NativeHumanDecisionScope.selectQuestion(actual, Purpose.GoalHuman)
      val changes = Vector(_reviewed.copy(dependency = _reviewed.dependency.copy(repository = "external-repository")),
        _reviewed.copy(dependency = _reviewed.dependency.copy(workspace = "dependency-code")),
        _reviewed.copy(paths = _reviewed.paths :+ "src/extra.scala"), _reviewed.copy(acceptance = Vector("Different acceptance")),
        _reviewed.copy(usageContract = ContextReference("usage-contract:other", "4")),
        _reviewed.copy(dependentWork = Vector(ContextReference("dependent-work:other", "1"))),
        _reviewed.copy(discoveredEdits = Vector(_reviewed.paths.last)))
      When("the existing source and exact Human selector consume both optionals and crossed logical work selections")
      val crossed = changes.map(value => NativeHumanDecisionScope.selectQuestion(_target(_question(value, observe = true)), Purpose.GoalHuman))
      Then("neither optional overwrites the other and every valid changed dependency target differs")
      source.hcursor.downField("result").downField("reviewObservations").focus shouldBe Some(_observations.asJson)
      source.hcursor.downField("result").downField("dependencyProposal").focus shouldBe Some(_reviewed.asJson)
      selected.isRight shouldBe true
      crossed.foreach { value => value.isRight shouldBe true; value should not be selected }

      Given("the same question with absent, malformed, null, unknown or dropped result selections")
      val resultjson = source.hcursor.downField("result").focus.get
      def _with_result_(value: Json): Json = actual.mapObject(fields => fields.add("source", _field(source, "result", value)))
      val malformed = Vector(Json.Null, Json.obj(), _field(resultjson, "unknown", Json.True),
        _field(resultjson, "dependencyProposal", Json.Null), _field(resultjson, "reviewObservations", Json.Null))
      When("the strict selector consumes malformed presence and independently dropped optionals")
      val refused = malformed.map(value => NativeHumanDecisionScope.selectQuestion(_with_result_(value), Purpose.GoalHuman))
      val dropped = Vector("dependencyProposal", "reviewObservations").map(name =>
        NativeHumanDecisionScope.selectQuestion(_with_result_(resultjson.mapObject(_.remove(name))), Purpose.GoalHuman))
      val ordinary = _target(_question().copy(current = {
        val decision = _question().current.asInstanceOf[WorkflowContinuation.Decision[H.Input]]
        val value = _input(_question()).copy(result = _result().copy(dependencyProposal = None))
        decision.copy(request = decision.request.copy(input = Some(TypedValue(H.inputCodec.typeIdentity, value))))
      }))
      Then("malformed presence fails, dropped meaning differs and existing absent-result selection remains valid")
      refused.foreach(_.isLeft shouldBe true)
      dropped.foreach { value => value.isRight shouldBe true; value should not be selected }
      NativeHumanDecisionScope.selectQuestion(ordinary, Purpose.GoalHuman).isRight shouldBe true
    }

    "retain complete independent review verdicts and reject malformed proposals and normalized Human name collisions" in {
      Given("the actual review verdict projection with both observations and the REVIEW dependency proposal")
      val value = _result(_reviewed).copy(reviewObservations = Some(_observations))
      val verdict = ThreeWorkflowExternalEvidencePorts.goalReviewVerdict(value)
      def _binding_(value: Json): Json = NativeReviewEvidenceFixture.binding(Purpose.GoalReview, verdict = Some(value))
      val selected = NativeReviewScope.select(Purpose.GoalReview, _binding_(verdict))
      When("the existing review selector consumes exact, changed, dropped and malformed dependency verdicts")
      val changed = NativeReviewScope.select(Purpose.GoalReview, _binding_(_field(verdict, "dependencyProposal",
        _reviewed.copy(acceptance = Vector("Different required outcome")).asJson)))
      val dropped = NativeReviewScope.select(Purpose.GoalReview, _binding_(verdict.mapObject(_.remove("dependencyProposal"))))
      val malformed = Vector(Json.Null, Json.obj(), _field(_reviewed.asJson, "discoveredEdits", Json.Null))
        .map(value => NativeReviewScope.select(Purpose.GoalReview, _binding_(_field(verdict, "dependencyProposal", value))))
      Then("full review meaning is retained, changed or dropped meaning differs and malformed presence fails")
      verdict.hcursor.downField("dependencyProposal").focus shouldBe Some(_reviewed.asJson)
      verdict.hcursor.downField("reviewObservations").focus shouldBe Some(_observations.asJson)
      selected.isRight shouldBe true
      changed.isRight shouldBe true
      changed should not be selected
      dropped.isRight shouldBe true
      dropped should not be selected
      malformed.foreach(_.isLeft shouldBe true)

      Given("equal aliases of dependencyProposal and a nested Usage Contract name in the existing Human wire")
      val wire = _target(_question(_reviewed, observe = true))
      def _with_result_(value: Json): Json = wire.mapObject(fields => fields.add("source",
        fields("source").get.mapObject(_.add("result", value))))
      val exact = wire.hcursor.downField("source").downField("result").focus.get
      val alias = exact.mapObject(fields => fields.remove("dependencyProposal").add("dependency_proposal", _reviewed.asJson))
      val collision = _field(exact, "dependency_proposal", _reviewed.asJson)
      val nested = _field(exact, "dependencyProposal", _field(_reviewed.asJson, "usage_contract",
        _reviewed.asJson.hcursor.downField("usageContract").focus.get))
      When("normal wire normalization feeds the existing strict question selector")
      val normalized = NativeHumanDecisionScope.selectQuestion(NativeHumanDecisionQuestionWire.normalize(_with_result_(alias), Purpose.GoalHuman), Purpose.GoalHuman)
      val refused = Vector(collision, nested).map(value => NativeHumanDecisionScope.selectQuestion(
        NativeHumanDecisionQuestionWire.normalize(_with_result_(value), Purpose.GoalHuman), Purpose.GoalHuman))
      Then("the single public alias normalizes but even equal competing names fail")
      normalized shouldBe NativeHumanDecisionScope.selectQuestion(wire, Purpose.GoalHuman)
      refused.foreach(_.isLeft shouldBe true)
    }
  }
}
