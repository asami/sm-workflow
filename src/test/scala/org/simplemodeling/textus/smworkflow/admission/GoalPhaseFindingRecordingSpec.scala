package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration => HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionQuestionWire
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseFindingRecording as R
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Pure specification input and admitted controller examples, never native effect proof.
  * @since   Oct. 18, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseFindingRecordingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _hygiene = "docs/journal/findings.md"
  private val _candidate = "docs/candidates.md"
  private val _policy = R.Policy(_hygiene, _candidate)
  private val _paths = Vector("one.scala", _hygiene, _candidate)
  private val _plan = NativeHumanEvidenceFixture.plan.copy(steps =
    NativeHumanEvidenceFixture.plan.steps.map(_.copy(ownedPaths = _paths)))
  private val _base = NativeHumanEvidenceFixture.snapshot
  private val _reference = ContextReference("independent-review", "7")
  private val _observation = R.Observation("hygiene:1", "HYGIENE", "Clarify a name", "The accepted behavior already satisfies the requirement")
  private val _idea = R.Observation("candidate:1", "DEVELOPMENT_CANDIDATE", "Consider a future feature", "It is outside this selected requirement")
  private val _observations = R.Observations(Vector(_observation, _idea))
  private val _closed = O.ClosedStep(_plan.steps.head.identity, O.Commit("c" * 40, _base.head, "d" * 40), "accepted-step-review")
  private val _items = Vector(R.Item(_reference, "ReviewStep", Some(_closed.step), _observation, _hygiene),
    R.Item(_reference, "ReviewStep", Some(_closed.step), _idea, _candidate))
  private val _request = R.Request("instance", "8", "recording-batch-1", _plan, _base, Vector(_closed),
    Some(_closed.step), _items, None, Some(GoalPhaseRepairProgress.Snapshot(0, 0, _plan.repairLimit)))
  private val _recorded = _items.map(i => R.ItemResult(i.sourceReference, i.observation.identity, "recorded",
    Vector(R.SavedSection(i.destination, "Finding " + i.observation.identity)), "Saved the assigned nonblocking finding"))
  private val _result = R.Result("completed", "All assigned findings recorded", _recorded)
  private val _work = NativeHumanEvidenceFixture.work.copy(selectedPlan = _plan)
  private def _payload(value: Json)(f: Json => Json): Json = value.mapObject(fields => fields.add("payload", f(fields("payload").get)))
  private def _review: O.State = {
    val initial = take(O.startC(_plan, _base))
    val key = initial.next.asInstanceOf[O.Semantic].key
    val implemented = take(initial.acceptC(O.SemanticCompleted(key, "Selected implementation complete")))
    val candidate = O.Snapshot(_base.head, "d" * 40)
    val inspected = take(implemented.acceptC(O.CandidateObserved(candidate)))
    take(inspected.acceptC(O.ChecksObserved(candidate, Vector("focused"), "selected-check", true)))
  }
  private def _configuration(value: Json): ConfigurationValue = value.fold(ConfigurationValue.NullValue,
    ConfigurationValue.BooleanValue.apply, n => ConfigurationValue.NumberValue(n.toBigDecimal.get),
    ConfigurationValue.StringValue.apply, values => ConfigurationValue.ListValue(values.map(_configuration).toList),
    fields => ConfigurationValue.ObjectValue(fields.toMap.view.mapValues(_configuration).toMap))
  private def _resolve(value: Option[ConfigurationValue]): Consequence[Option[R.Policy]] = {
    var selected = Option.empty[R.Policy]
    val id = SmWorkflowComponent.componentId
    val creation = ComponentCreate(RuntimeBindingAdmissionFixture.admit(Subsystem(name = "spec-recording", version = None,
      configuration = ResolvedConfiguration(HostConfiguration(value.map(R.declaration.name -> _).toMap), ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command)), ComponentOrigin.Builtin,
      Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name), version = Some("0.1.0"),
        schemaVersion = Some(3), componentId = Some(id))))
    val factory = new SmWorkflowConfiguredComponentFactory(Vector(R.declaration), Vector.empty,
      (_, parameters, _) => R.resolvePolicyC(parameters).map { value =>
        selected = value
        Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
      })
    factory.primaryFactory.createPrimaryC(creation).map(_ => selected)
  }

  "E1 review observations" should afterWord("in spec:goal-phase-finding-recording, rules:R1,R5") {
    "accept nonblocking review observations without weakening required findings and refuse implementation or repair presence" in {
      Given("an actual pure controller at its assigned Step review and separate nonblocking observations")
      val result = S.Result("accept", "Required behavior is correct", Vector.empty, reviewObservations = Some(_observations))
      When("the actual review completion owner admits the observations alongside its existing required-finding guards")
      val accepted = GoalPhaseNativeReview.completedStateC(result, "independent-review", _review)
      Then("acceptance remains valid and observations do not become required findings")
      accepted.isSuccess shouldBe true
      Vector("ReviewStep", "ReviewPhase").foreach(kind => R.admitObservationsC(kind, Some(_observations)).isSuccess shouldBe true)
      GoalPhaseNativeReview.completedStateC(result.copy(findings = Vector("Required blocker")), "independent-review", _review).isSuccess shouldBe false
      val repair = result.copy(outcome = "repair", findings = Vector("Required blocker"))
      GoalPhaseNativeReview.completedStateC(repair, "independent-review", _review).isSuccess shouldBe true

      Given("implementation and an actual retained-review repair request with either populated or explicitly empty observations")
      val repairwork = _work.copy(kind = "RepairStep", repairDirective = Some(O.RepairDirective("Required fix", Vector("Required blocker"))),
        review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "actual selected diff", Vector.empty)))
      When("the ordinary semantic admission owners consume each field presence")
      val refused = Vector(_observations, R.Observations(Vector.empty)).flatMap { observations =>
        val completion = S.Result("completed", "Assigned work complete", Vector.empty, reviewObservations = Some(observations))
        Vector(S.admitInitialResultC(_work, completion), S.admitSemanticResultC(repairwork, completion))
      }
      Then("neither work kind can claim review observations even when the vector is empty")
      refused.foreach(_.isSuccess shouldBe false)
    }
  }

  "E2 observation wire and projections" should afterWord("in spec:goal-phase-finding-recording, rules:R1,R5") {
    "retain absence and reject malformed duplicate unknown category and explicit null observation content" in {
      Given("a historical result without observations and a result with explicit nonblocking observations")
      val absent = S.Result("accept", "Selected review complete", Vector.empty)
      val present = absent.copy(reviewObservations = Some(_observations))
      When("the saved result codec and exact public projections carry their selected contents")
      val wire = S.resultCodec.encode(present)
      val publicvalue = take(R.publicObservationsC(present.reviewObservations))
      Then("absence stays absent and the new field round trips without losing content")
      S.resultCodec.encode(absent).hcursor.downField("payload").downField("reviewObservations").focus shouldBe None
      S.resultCodec.decode(S.resultCodec.encode(absent)) shouldBe Right(absent)
      S.resultCodec.decode(wire) shouldBe Right(present)
      take(R.internalObservationsC(publicvalue)) shouldBe Some(_observations)
      R.admitObservationsC("ReviewStep", Some(R.Observations(Vector.empty))).isSuccess shouldBe true

      Given("nulls invalid identities categories summaries rationales duplicates and excessive vectors")
      val baditems = Vector(null, _observation.copy(identity = " padded "), _observation.copy(identity = "a" * 129),
        _observation.copy(category = "REQUIRED"), _observation.copy(summary = " "),
        _observation.copy(rationale = "x\u0000y"), _observation.copy(summary = "あ" * 5462))
      val malformed = baditems.map(i => R.Observations(Vector(i))) ++ Vector(R.Observations(null),
        R.Observations(Vector(_observation, _observation)), R.Observations(Vector.tabulate(129)(i => _observation.copy(identity = "h" + i))))
      When("typed admission and saved JSON decoding consume those values")
      val failed = malformed.map(v => R.validateObservationsOptionC(Some(v))) ++ Vector(
        R.validateObservationsOptionC(null), R.validateObservationsOptionC(Some(null)),
        R.internalObservationsC(Some(V.GoalPhaseReviewObservations(null))),
        R.internalObservationsC(Some(V.GoalPhaseReviewObservations(Vector(null)))))
      val badwire = Vector(Json.Null, Json.True, Json.obj("items" -> Json.Null),
        Json.obj("items" -> Json.arr(_observation.asJson, _observation.asJson)),
        Json.obj("items" -> Json.arr(_observation.asJson.mapObject(_.add("category", Json.fromString("UNKNOWN"))))))
      Then("all malformed forms fail and null-preserving projections do not turn them into absence")
      failed.foreach(_.isSuccess shouldBe false)
      badwire.foreach(v => S.resultCodec.decode(_payload(wire)(_.mapObject(_.add("reviewObservations", v)))).isLeft shouldBe true)
      R.publicObservations(null) shouldBe null
      R.publicObservations(Some(null)) shouldBe Some(null)
    }
  }

  "E3 configured explicit destinations" should afterWord("in spec:goal-phase-finding-recording, rules:R2") {
    "resolve absent object and string policy forms and refuse undeclared literal destinations" in {
      Given("an optional Component parameter and a Plan explicitly owning both recording files")
      val json = Json.obj("schemaVersion" -> Json.fromString(R.schemaVersion), "hygiene" -> Json.fromString(_hygiene),
        "developmentCandidate" -> Json.fromString(_candidate))
      When("the existing Component initialization owner resolves absent object and JSON-string forms")
      val policies = Vector(_configuration(json), ConfigurationValue.StringValue(json.noSpaces)).map(v => take(_resolve(Some(v))))
      Then("absence stays optional and both configured forms select the same exact destinations")
      take(_resolve(None)) shouldBe None
      policies shouldBe Vector(Some(_policy), Some(_policy))
      _policy.validateC(_plan, _paths).isSuccess shouldBe true
      R.Policy(_hygiene, _hygiene).validateC(_plan, Vector(_hygiene)).isSuccess shouldBe true

      Given("a second Step omitting the destinations, prefix-only ownership, missing host candidates and malformed configuration")
      val second = _plan.steps.head.copy(identity = "second", ownedPaths = Vector("two.scala"), dependencies = Vector(_closed.step))
      val prefix = _plan.copy(steps = _plan.steps.map(_.copy(ownedPaths = Vector("one.scala", "docs"))))
      When("policy validation and decoding consume those unassigned or invalid destinations")
      val denied = Vector(_policy.validateC(_plan.copy(steps = _plan.steps :+ second), _paths),
        _policy.validateC(prefix, _paths), _policy.validateC(_plan, Vector("docs")),
        R.Policy("../outside.md", _candidate).validateC(_plan, _paths), R.resolvePolicyC(null))
      val malformed = Vector(Json.Null, json.mapObject(_.add("other", Json.True)),
        json.mapObject(_.add("hygiene", Json.Null)), json.mapObject(_.add("developmentCandidate", Json.fromString("/absolute"))),
        json.mapObject(_.add("schemaVersion", Json.fromString("other"))))
      Then("neither prefixes nor malformed values authorize a destination")
      denied.foreach(_.isSuccess shouldBe false)
      malformed.foreach(v => R.decodePolicyC(v).isSuccess shouldBe false)
      _resolve(Some(ConfigurationValue.StringValue("invalid-json"))).isSuccess shouldBe false
    }
  }

  "E4 finite assigned payloads" should afterWord("in spec:goal-phase-finding-recording, rules:R3,R4,R5") {
    "admit valid ordered Step and Phase requests and all-recorded replies through distinct validated codecs" in {
      Given("a real pure Plan fixture and its ordered completed Step with exact assigned source keys and destinations")
      val phase = _request.copy(stepIdentity = None, items = _items.map(_.copy(sourceKind = "ReviewPhase", stepIdentity = None)))
      When("finite request and result codecs decode their own business payloads and shared result admission checks assignments")
      val requests = Vector(_request, phase)
      Then("both input scopes and their exact all-recorded replies are admitted without proving native history or effects")
      requests.foreach { request =>
        R.validateRequestC(request).isSuccess shouldBe true
        R.inputCodec.decode(R.inputCodec.encode(request)) shouldBe Right(request)
        R.admitResultC(request, _result).isSuccess shouldBe true
      }
      R.resultCodec.decode(R.resultCodec.encode(_result)) shouldBe Right(_result)
      R.inputCodec.typeIdentity shouldBe "GoalPhaseFindingRecordingRequest"
      R.resultCodec.typeIdentity shouldBe "GoalPhaseFindingRecordingResult"
      R.resultCodec.decode(R.inputCodec.encode(_request)).isLeft shouldBe true

      Given("no items, incomplete/wrong completed scope, invalid Git identities or broken parent chains and explicit null options")
      val badrequests = Vector(_request.copy(items = Vector.empty), _request.copy(completed = Vector.empty),
        _request.copy(stepIdentity = Some("another-step")), _request.copy(items = _items :+ _items.head),
        _request.copy(originalBase = _base.copy(head = "a" * 64)),
        _request.copy(completed = Vector(_closed.copy(commit = _closed.commit.copy(parent = "e" * 40)))),
        _request.copy(completed = Vector(_closed.copy(review = " "))),
        _request.copy(items = _items.updated(0, _items.head.copy(sourceKind = "ReviewPhase", stepIdentity = None))),
        _request.copy(requiredIssues = Some(null)), _request.copy(repairProgress = null))
      When("the request input checks consume the impossible finite assignments")
      val denied = badrequests.map(R.validateRequestC)
      val wire = R.inputCodec.encode(_request)
      Then("issuance input refuses each invalid scope and empty work is left for the native owner to skip")
      denied.foreach(_.isSuccess shouldBe false)
      Vector("stepIdentity", "requiredIssues", "repairProgress").foreach { key =>
        R.inputCodec.decode(_payload(wire)(_.mapObject(_.add(key, Json.Null)))).isLeft shouldBe true
      }
      R.inputCodec.decode(_payload(wire)(_.mapObject(_.add("selectedPlan", _plan.asJson.mapObject(
        _.add("acceptance", Json.Null)))))).isLeft shouldBe true
    }
  }

  "E5 truthful partial and failed outcomes" should afterWord("in spec:goal-phase-finding-recording, rules:R4") {
    "retain mixed recorded failed and partial item dispositions without claiming completion" in {
      Given("two assigned findings with actual saved sections for one and a failure for the other")
      val failed = _recorded.map(_.copy(outcome = "failed", records = Vector.empty, rationale = "Assigned file could not be updated"))
      val mixed = _result.copy(outcome = "partial", items = Vector(_recorded.head, failed.last))
      val partial = _result.copy(outcome = "partial", items = _recorded.updated(0, _recorded.head.copy(outcome = "partial")))
      val allfailed = _result.copy(outcome = "failed", items = failed)
      When("the same exact finite assignment admits each truthful overall result")
      val replies = Vector(mixed, partial, allfailed)
      Then("partial and failed outcomes round trip while a false completed or failed aggregate is refused")
      replies.foreach { reply =>
        R.admitResultC(_request, reply).isSuccess shouldBe true
        R.resultCodec.decode(R.resultCodec.encode(reply)) shouldBe Right(reply)
      }
      Vector(mixed.copy(outcome = "completed"), partial.copy(outcome = "failed"), allfailed.copy(outcome = "partial"))
        .foreach(reply => R.admitResultC(_request, reply).isSuccess shouldBe false)
    }
  }

  "E6 exact recording correspondence and Human result binding" should afterWord("in spec:goal-phase-finding-recording, rules:R3,R4,R5") {
    "refuse missing duplicate extra reordered crossed source or destination and invalid saved sections" in {
      Given("one exact finite recording assignment and its all-recorded result")
      val first = _recorded.head
      val baditems = Vector(_recorded.take(1), _recorded :+ first, _recorded :+ first.copy(identity = "extra"), _recorded.reverse,
        _recorded.updated(0, first.copy(sourceReference = _reference.copy(revision = "other"))),
        _recorded.updated(0, first.copy(sourceReference = _reference.copy(identity = "other-review"))),
        _recorded.updated(0, first.copy(records = Vector(R.SavedSection(_candidate, "Crossed destination")))),
        _recorded.updated(0, first.copy(records = Vector(R.SavedSection(_hygiene + "/child", "Child path")))),
        _recorded.updated(0, first.copy(records = Vector(R.SavedSection(_hygiene, "")))),
        _recorded.updated(0, first.copy(records = first.records ++ first.records)),
        _recorded.updated(0, first.copy(records = Vector.empty)),
        _recorded.updated(0, first.copy(outcome = "failed")))
      When("result admission compares the ordered business keys and exact destinations")
      val denied = baditems.map(items => R.admitResultC(_request, _result.copy(items = items)))
      Then("no item may disappear duplicate move cross its source or file or fabricate a saved section")
      denied.foreach(_.isSuccess shouldBe false)
      R.resultCodec.decode(_payload(R.resultCodec.encode(_result))(_.mapObject(_.add("newTasks", Json.arr())))).isLeft shouldBe true
    }

    "retain observations in Human selected result nodes and normalize the actual public declaration wire name" in {
      Given("an issued Human fixture question retaining a ReviewStep result with nonblocking observations")
      val question = NativeHumanEvidenceFixture.goalQuestion
      val decision = question.current.asInstanceOf[org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]]
      val original = decision.request.input.get
      val input = original.value.copy(work = _work.copy(kind = "ReviewStep"),
        result = S.Result("decision-needed", "Explicit review question", Vector.empty, reviewObservations = Some(_observations)))
      val issued = question.copy(current = decision.copy(request = decision.request.copy(input = Some(original.copy(value = input)))))
      When("the actual Human source and native scope select the optional result observations")
      val source = take(ThreeWorkflowExternalEvidencePorts.goalHumanSourceC(issued))
      val binding = take(NativeHumanDecisionScope.questionBindingC(issued, source))
      val selected = NativeHumanDecisionScope.selectQuestion(binding, NativeExternalEvidence.Purpose.GoalHuman).toOption.get
      val snake = binding.hcursor.downField("source").downField("result").withFocus(_.mapObject(fields =>
        fields.remove("reviewObservations").add("review_observations", fields("reviewObservations").get))).top.get
      val normalized = NativeHumanDecisionQuestionWire.normalize(snake, NativeExternalEvidence.Purpose.GoalHuman)
      Then("both declaration and generated wire names retain the same exact content and absent sources remain unchanged")
      selected.hcursor.downField("source").downField("result").get[Json]("reviewObservations") shouldBe Right(_observations.asJson)
      NativeHumanDecisionScope.selectQuestion(normalized, NativeExternalEvidence.Purpose.GoalHuman) shouldBe Right(selected)
      val changed = binding.hcursor.downField("source").downField("result").downField("reviewObservations")
        .downField("items").downArray.withFocus(_.mapObject(_.add("rationale", Json.fromString("Different selected rationale")))).top.get
      NativeHumanDecisionScope.selectQuestion(changed, NativeExternalEvidence.Purpose.GoalHuman).toOption.get should not be selected
      val historical = take(ThreeWorkflowExternalEvidencePorts.goalHumanSourceC(question))
      historical.hcursor.downField("result").focus shouldBe None
      val nullresult = binding.hcursor.downField("source").downField("result").withFocus(_.mapObject(
        _.add("reviewObservations", Json.Null))).top.get
      NativeHumanDecisionScope.selectQuestion(nullresult, NativeExternalEvidence.Purpose.GoalHuman).isLeft shouldBe true
    }
  }
}
