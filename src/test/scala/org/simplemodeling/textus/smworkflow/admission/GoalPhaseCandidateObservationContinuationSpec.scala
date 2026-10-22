package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.processexecution.GitDiffParameters
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeCandidateObservationContinuationPreparation as P
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseCandidateObservationContinuation as C
import GoalPhaseCandidateRecovery as R
import GoalPhaseNativeCandidate as N
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Independent continuation schema and evidence contracts; fixture answers are not real consent.
  * @since   Oct. 22, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseCandidateObservationContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _path = "src/main/scala/Example.scala"
  private val _now = Instant.parse("2026-10-22T00:00:00Z")
  private val _reference = V.WorkflowArtifactReference("fresh-continuation-human", "1")
  private val _answer = V.GoalPhaseCandidateObservationContinuationAnswer("AuthorizeNewObservation", "Accept only the described new observation; all prior outcomes remain UNKNOWN")
  private val _execution = V.WorkflowExecutionEvidence(Vector(_reference), Some("participant"), None, None)
  private def _target: C.Target = {
    val paths = Vector(_path)
    val plan = O.Plan("continuation-plan", "2", "phase-116", "fixture-repository",
      Vector(O.Step("step-1", "Complete owned candidate", paths, Vector.empty, Vector("test"))), Vector("test"))
    val initial = take(O.startC(plan, _base))
    val state = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Fixture implementation")))
    val inspection = N.Payload("50", "step-1", paths, Vector.empty, None, Vector.empty, None)
    def _id_(key: String) = "fixture-session:ordered-candidate:50:" + key
    val original = R.RecoveryTarget("fixture-session", R.controllerSnapshot(state), "goal-phase", "1", "participant", "50", "53",
      "/private/tmp/fixture-continuation-repository", "main", paths, Vector.empty, _id_("inspection"), "1", "51", inspection,
      WorkflowSourceHistoryRule.Selection("none", None), Vector.empty,
      Vector(R.StagePair(0, _id_("stage-intent-0"), "1", "52", _id_("stage-result-0"), "1", "53",
        inspection, inspection.copy(stagedPaths = paths))), "UNKNOWN", "termination-unconfirmed")
    C.Target(original, original.controller, "fixture-session:ordered-candidate-recovery:50", "1", "54", "54", "55",
      Vector(C.PriorObservation("50", "54", "fixture-session:ordered-candidate:54:observation-intent", "1", "55",
        original.stagingPrefix.last.result.copy(sourceRevision = "54"), "UNKNOWN", "termination-unconfirmed")),
      GitDiffParameters.defaultBytes, C.RepositorySnapshot(original.root, "main", _base.head, _base.tree,
        Vector(C.Dirty("M", " ", _path, None)), "c" * 40))
  }
  private def _description(target: C.Target): V.GoalPhaseCandidateObservationContinuationDescriptionResult =
    V.GoalPhaseCandidateObservationContinuationDescriptionResult(target.original.sessionId, "decision-required", C.message,
      "component", C.targetRecord(target), C.choicesFor(target), C.continuationOperation)

  "Candidate observation continuation E1 complete retained schema" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E1, rules:R1,R2,R4, phase:CC04") {
    "roundtrip the unchanged original target full chain snapshot budget and uncertainty" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R1 R2 R4 E1: original complete staging and one failed authorized intent")
      val first = _target
      val second = first.copy(directionSourceRevision = "56", frontierRevision = "57", priorObservations =
        first.priorObservations :+ C.PriorObservation("54", "56", "fixture-session:ordered-candidate:56:observation-intent", "1", "57",
          first.original.stagingPrefix.last.result.copy(sourceRevision = "56"), "UNKNOWN", "termination-unconfirmed"))
      val direction = C.Direction(second, ContextReference(_reference.identity, _reference.revision), "participant", _answer.rationale)
      When("public target naming and the distinct v1 durable direction are interpreted")
      val restored = C.targetC(RuntimeContext.PropertyNameContext.default.transformRecord(C.targetRecord(second)))
      val replayed = C.codec.decode(C.codec.encode(direction))
      Then("all original and subsequent immutable facts survive and no old recovery schema is accepted")
      take(restored) shouldBe second
      replayed shouldBe Right(direction)
      C.validTarget(first) shouldBe true
      C.choicesFor(first) shouldBe Vector("AuthorizeNewObservation", "Defer")
      second.original shouldBe first.original
      second.priorObservations.foreach { p => p.outcome shouldBe "UNKNOWN"; p.termination shouldBe "termination-unconfirmed" }
      C.codec.decode(R.codec.encode(R.Direction(first.original, ContextReference("old", "1"), "participant", "Old answer"))).isLeft shouldBe true
      C.message should include("write-tree")
      C.message should include("Engineering approval")
    }
  }

  "Candidate observation continuation E2 crossed chain and repository" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E2, rules:R1,R2,R4, phase:CC04") {
    "refuse incomplete noncontiguous settled or foreign target facts" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R1 R2 R4 E2: an exact complete target with one prior UNKNOWN observation")
      val t = _target
      val p = t.priorObservations.head
      val invalid = Vector(t.copy(priorObservations = Vector.empty), t.copy(priorObservations = Vector.fill(65)(p)),
        t.copy(priorObservations = Vector(p.copy(intentRecordRevision = "56"))),
        t.copy(priorObservations = Vector(p.copy(directionSourceRevision = "49"))),
        t.copy(priorObservations = Vector(p.copy(intentIdentity = "foreign:observation-intent"))),
        t.copy(priorObservations = Vector(p.copy(intent = p.intent.copy(stagedPaths = Vector.empty)))),
        t.copy(priorObservations = Vector(p.copy(outcome = "NotExecuted"))),
        t.copy(priorObservations = Vector(p.copy(termination = "terminated"))),
        t.copy(frontierRevision = "56"), t.copy(directionSourceRevision = "55"),
        t.copy(originalDirectionIdentity = "foreign-direction"), t.copy(originalDirectionRecordRevision = "55"),
        t.copy(controller = t.controller.copy(generation = t.controller.generation + 1)),
        t.copy(original = t.original.copy(stagingPrefix = Vector.empty)),
        t.copy(repository = t.repository.copy(root = "/private/tmp/foreign")),
        t.copy(repository = t.repository.copy(branch = "foreign")),
        t.copy(repository = t.repository.copy(head = "d" * 40)),
        t.copy(repository = t.repository.copy(indexTree = "invalid")),
        t.copy(repository = t.repository.copy(dirty = Vector(C.Dirty("U", "U", _path, None)))),
        t.copy(repository = t.repository.copy(dirty = Vector.fill(2)(C.Dirty("M", " ", _path, None)))),
        t.copy(repository = t.repository.copy(dirty = Vector(C.Dirty("R", " ", _path, Some("foreign.txt"))))),
        t.copy(repository = t.repository.copy(dirty = Vector(C.Dirty("M", " ", "foreign.txt", None)))),
        t.copy(maximumDiffBytes = 0L), t.copy(maximumDiffBytes = GitDiffParameters.maximumBytes + 1))
      When("each shape is interpreted as a continuation target")
      val results = invalid.map(value => C.decodeTarget(value.asJson))
      Then("no incomplete prefix snapshot or contradictory uncertainty can authorize observation")
      results.foreach(_.isLeft shouldBe true)
    }
  }

  "Candidate observation continuation E5 scoped semantic revision" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E5, rules:R2,R3,R5, phase:CC04") {
    "describe MM and selected untracked changes only for explicit revision work or Defer" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R2 R3 R5 E5: safe owned MM and untracked snapshots after a failed observation")
      val original = _target
      val targets = Vector(C.Dirty("M", "M", _path, None), C.Dirty("?", "?", _path, None)).map(dirty =>
        original.copy(repository = original.repository.copy(dirty = Vector(dirty))))
      val reference = ContextReference(_reference.identity, _reference.revision)
      When("the public target choices binding and passive preparation interpret each exact decoded snapshot")
      val descriptions = targets.map(target => _description(take(C.targetC(C.targetRecord(target)))))
      val prepared = descriptions.map(description => P.prepareC(description, _answer.copy(choice = "ReviseWork"),
        _reference, _execution, "participant", "test-host", _now))
      Then("revision is explicit and an observation answer or guessed description list cannot authorize it")
      targets.zip(descriptions).foreach { (target, description) =>
        C.validTarget(target) shouldBe true
        C.observationEligible(target) shouldBe false
        description.choices shouldBe Vector("ReviseWork", "Defer")
        C.bindingC("component", target, "AuthorizeNewObservation", _answer.rationale, "participant", reference).isSuccess shouldBe false
        C.bindingC("component", target, "ReviseWork", _answer.rationale, "participant", reference).isSuccess shouldBe true
        C.bindingC("component", target, "Defer", _answer.rationale, "participant", reference).isSuccess shouldBe true
        P.prepareC(description.copy(choices = Vector("AuthorizeNewObservation", "ReviseWork", "Defer")),
          _answer.copy(choice = "ReviseWork"), _reference, _execution, "participant", "test-host", _now).isSuccess shouldBe false
        P.prepareC(description, _answer, _reference, _execution, "participant", "test-host", _now).isSuccess shouldBe false
      }
      prepared.foreach(_.isSuccess shouldBe true)
      C.bindingC("component", original, "ReviseWork", _answer.rationale, "participant", reference).isSuccess shouldBe false
      C.choicesFor(original.copy(repository = original.repository.copy(dirty = Vector.empty))) shouldBe Vector("AuthorizeNewObservation", "Defer")
    }
  }

  "Candidate observation continuation E6 durable compatibility" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E6, rules:R3,R4,R5, phase:CC04") {
    "retain exact legacy bytes and decode only an explicit eligible v2 ReviseWork" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R3 R4 R5 E6: historical choice-free v1 and a new owned MM target")
      val target = _target
      val reference = ContextReference(_reference.identity, _reference.revision)
      val legacy = C.Direction(target, reference, "participant", _answer.rationale)
      val oldwire = Json.obj("schemaVersion" -> "sm-workflow.goal-phase-candidate-observation-continuation.v1".asJson,
        "payload" -> Json.obj("target" -> target.asJson, "reference" -> Json.obj("identity" -> reference.identity.asJson,
          "revision" -> reference.revision.asJson), "participant" -> "participant".asJson, "rationale" -> _answer.rationale.asJson))
      val revised = legacy.copy(target = target.copy(repository = target.repository.copy(
        dirty = Vector(C.Dirty("M", "M", _path, None)))), choice = "ReviseWork")
      val wire = C.codec.encode(revised)
      def _choice_(value: Json) = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get.mapObject(_.add("choice", value))))
      val invalid = Vector(wire.mapObject(_.add("schemaVersion", "unknown".asJson)),
        oldwire.mapObject(_.add("choice", "ReviseWork".asJson)),
        wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get.mapObject(_.remove("choice")))),
        _choice_(Json.Null), _choice_("Defer".asJson), _choice_("AuthorizeNewObservation".asJson), _choice_("unknown".asJson),
        oldwire.mapObject(_.add("payload", legacy.asJson)),
        oldwire.mapObject(_.add("payload", revised.asJson)),
        oldwire.mapObject(_.add("payload", revised.asJson.mapObject(_.remove("choice")))),
        C.codec.encode(revised.copy(target = target)), C.codec.encode(legacy.copy(choice = "Defer")))
      When("legacy and new receipts are decoded and encoded without rewriting historical receipts")
      val decoded = C.codec.decode(oldwire)
      val restored = C.codec.decode(wire)
      Then("v1 remains its exact observation-only byte shape and every illegal injected or absent choice refuses")
      decoded shouldBe Right(legacy)
      C.codec.encode(decoded.toOption.get).noSpaces shouldBe oldwire.noSpaces
      restored shouldBe Right(revised)
      wire.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.goal-phase-candidate-observation-continuation.v2")
      invalid.foreach(value => C.codec.decode(value).isLeft shouldBe true)
    }
  }

  "Candidate observation continuation E3 independent complete binding" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E3, rules:R2,R3, phase:CC04") {
    "reject stale budget snapshot foreign component participant reference and old purpose evidence" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R2 R3 E3: distinct independent evidence for the exact described target")
      val target = _target
      val reference = ContextReference(_reference.identity, _reference.revision)
      def _binding_(t: C.Target = target, component: String = "component", ref: ContextReference = reference) =
        take(C.bindingC(component, t, _answer.choice, _answer.rationale, t.original.participant, ref))
      val binding = _binding_()
      val declaration = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
        EntityCollectionId("smworkflow", "cli", "host_evidence")), "test-host")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, C.purpose, "lookup", reference.identity,
        reference.revision, declaration.issuer, "participant", binding, _now.toString)
      val stale = Vector(_binding_(target.copy(maximumDiffBytes = 131072L)),
        _binding_(target.copy(repository = target.repository.copy(indexTree = "d" * 40))),
        _binding_(component = "foreign-component"), _binding_(ref = ContextReference("foreign-answer", "1")))
      When("the independent provider compares the full current request and distinct purpose")
      val accepted = NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateObservationContinuation,
        "lookup", binding, _now)
      val refused = stale.map(value => NativeExternalEvidence.observeC(evidence, declaration,
        NativeExternalEvidence.Purpose.CandidateObservationContinuation, "lookup", value, _now))
      Then("only the separately provisioned exact answer admits")
      accepted.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      C.selectBinding(binding) shouldBe Right(binding)
      C.bindingC("component", target, _answer.choice, _answer.rationale, "foreign-participant", reference).isSuccess shouldBe false
      C.bindingC("component", target, "AuthorizeNewAttempt", _answer.rationale, "participant", reference).isSuccess shouldBe false
      C.selectBinding(take(R.bindingC("component", target.original, _answer.choice, _answer.rationale, "participant", reference))).isLeft shouldBe true
      NativeExternalEvidence.observeC(evidence.copy(purpose = R.purpose), declaration,
        NativeExternalEvidence.Purpose.CandidateObservationContinuation, "lookup", binding, _now).isSuccess shouldBe false
    }
  }

  "Candidate observation continuation E4 passive fresh preparation" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E4, rules:R2,R3, phase:CC04") {
    "prepare only its distinct purpose for explicit authorization or Defer with exact supplied identities" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R2 R3 E4: a fresh actual description and independently supplied Human answer")
      val description = _description(_target)
      When("the passive preparation interprets authorization and separately authenticated Defer")
      val prepared = take(P.prepareC(description, _answer, _reference, _execution, "participant", "test-host", _now))
      val deferred = take(P.prepareC(description, _answer.copy(choice = "Defer"), _reference, _execution, "participant", "test-host", _now))
      Then("the resulting import has the distinct complete binding and performs no workflow effect")
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      evidence.purpose shouldBe C.purpose
      evidence.binding.hcursor.downField("candidateObservationContinuation").downField("answer").get[String]("choice").toOption shouldBe Some("AuthorizeNewObservation")
      take(NativeExternalEvidence.decodeStorageC(deferred.record.getString(NativeExternalEvidence.storageField).get))
        .binding.hcursor.downField("candidateObservationContinuation").downField("answer").get[String]("choice").toOption shouldBe Some("Defer")
      prepared.recordIdentity should startWith(C.purpose + ":")
      Given("a missing reference or crossed session operation participant worker or choices")
      When("each contradictory passive preparation is requested")
      val refused = Vector(P.prepareC(description, _answer, null, _execution, "participant", "test-host", _now),
        P.prepareC(description.copy(sessionId = "foreign"), _answer, _reference, _execution, "participant", "test-host", _now),
        P.prepareC(description.copy(continuationOperation = R.recoveryOperation), _answer, _reference, _execution, "participant", "test-host", _now),
        P.prepareC(description.copy(choices = Vector("Defer")), _answer, _reference, _execution, "participant", "test-host", _now),
        P.prepareC(description, _answer, _reference, _execution, "foreign", "test-host", _now),
        P.prepareC(description, _answer, _reference, _execution.copy(workerIdentity = None), "participant", "test-host", _now))
      Then("no missing or crossed Human fact is converted to evidence")
      refused.foreach(_.isSuccess shouldBe false)
    }
  }
}
