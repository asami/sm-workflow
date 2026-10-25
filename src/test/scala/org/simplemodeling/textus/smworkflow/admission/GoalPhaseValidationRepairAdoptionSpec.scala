package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseValidationRepairAdoption as A
import GoalPhaseOrderedProgression as O
import GoalPhaseValidationPriorInvocation as P
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.preparation.NativeValidationRepairPreparation
import SplitPhaseExplicitPartitionFixture.take

/** Pure exact-contract controls; these values supply no actual execution or Human authority.
  * @since Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseValidationRepairAdoptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _commit = O.Commit("c" * 40, _base.head, "d" * 40)
  private val _plan = O.Plan("repair-plan", "1", "phase-fixture", "fixture-repository",
    Vector(O.Step("S1", "Retain accepted source", Vector("src/main"), Vector.empty, Vector("test"))), Vector("test"), 2)
  private val _proposed = _plan.copy(revision = "2", steps = _plan.steps :+
    O.Step("S2", "Correct finite diagnostic output", Vector("src/test/ProductCheck.scala"), Vector("S1"), Vector("test")))
  private val _proposal = GoalPhasePlanRevision.Proposal("corrective-direction", "1", "Correct actual bounded output", _proposed)
  private val _source = Json.obj("kind" -> "output-limited".asJson, "sessionId" -> "fixture-session".asJson,
    "workflowIdentity" -> "GoalPhaseWorkflow".asJson, "workflowRevision" -> "1".asJson, "participant" -> "participant".asJson,
    "planIdentity" -> _plan.identity.asJson, "planRevision" -> _plan.revision.asJson, "base" -> _base.asJson,
    "candidate" -> O.Snapshot(_commit.identity, _commit.tree).asJson, "sourceRevision" -> "10".asJson,
    "frontierRevision" -> "13".asJson, "step" -> "phase:phase-fixture".asJson, "checkIdentity" -> "test".asJson,
    "checkOrdinal" -> 1.asJson, "attempt" -> 0.asJson, "intent" -> A.ReceiptRef("fixture-session:ordered-validation:10:check-intent-0", "11").asJson,
    "result" -> A.ReceiptRef("fixture-session:ordered-validation:10:check-result-0", "12").asJson,
    "validation" -> A.ReceiptRef("fixture-session:ordered-validation:10:validation", "13").asJson,
    "termination" -> "output-limit-exceeded".asJson, "stream" -> "stderr".asJson, "outcome" -> "incomplete".asJson)
  private val _target = A.Target("sm-workflow.goal-phase-validation-repair-target.v1", _source, _plan,
    Vector(O.ClosedStep("S1", _commit, "independent-review")), GoalPhaseRepairProgress.Snapshot(1, 1, 2), 4,
    Vector("src/main", "src/test/ProductCheck.scala"), GoalPhaseNativeClosure.Policy("fixture-closure", 1,
      Vector(GoalPhaseNativeClosure.StepProgram("S1", 1), GoalPhaseNativeClosure.StepProgram("S2", 2))),
    GoalPhasePlanRevision.Captured(_proposal, _proposed), Vector(GoalPhaseNativeValidation.Check("test", 1)),
    Json.obj("schema" -> "cncf.runtime-process-execution.v3".asJson, "sbtPrograms" -> Json.arr()).noSpaces, A.nativeLimitations)
  private val _direction = A.Direction(_target, _proposal, "AdoptRepairPlan", "Actual independent fixture answer", "participant", ContextReference("human", "1"))

  "Exact validation repair contract" should afterWord("in spec:goal-phase-validation-repair-adoption, example:E2, rules:R2,R4,R5") {
    "require the historical-specific Human choice while disclosing the unresolved result effects and caller provenance" in {
      Given("a structural historical snapshot with an explicitly unobserved native result")
      val locator = V.WorkflowCodexSourceLocator("fixture-thread", "fixture-turn", "fixture-item")
      val policy = P.CallerSourcePolicy("sm-workflow.validation-repair-caller-source-policy.v1", locator.threadId,
        "/private/tmp/installed/sm-workflow", "/private/tmp/runtime", "/private/tmp/runtime/target/sm-workflow/tasks/repair")
      val item = Json.obj("type" -> "commandExecution".asJson, "id" -> locator.itemId.asJson,
        "command" -> P.command(policy, "fixture-session", "old-advance").asJson, "cwd" -> policy.runtimeRoot.asJson,
        "status" -> "failed".asJson, "exitCode" -> 1.asJson, "durationMs" -> 10.asJson)
      val snapshot = P.PriorInvocationSnapshot("sm-workflow.validation-prior-invocation-snapshot.v1", locator,
        ContextReference("codex:fixture-thread:fixture-turn:fixture-item", "1"), "file:///private/tmp/public-page.json",
        "2026-10-24T00:00:00Z", item.noSpaces, policy, "old-advance", "caller-invocation-terminated-only")
      val recovery = GoalPhaseValidationRecovery.RecoveryTarget("fixture-session", _plan.identity, _plan.revision,
        _base, O.Snapshot(_commit.identity, _commit.tree), "10", "11", "phase:" + _plan.phase, "test", 1, 0, "11")
      val source = Json.obj("kind" -> "historical-unobserved-result".asJson, "sessionId" -> "fixture-session".asJson,
        "workflowIdentity" -> "GoalPhaseWorkflow".asJson, "workflowRevision" -> "1".asJson, "participant" -> "participant".asJson,
        "recovery" -> recovery.asJson, "intent" -> A.ReceiptRef("fixture-session:ordered-validation:10:check-intent-0", "11").asJson,
        "resultDisposition" -> "unobserved".asJson, "priorInvocation" -> snapshot.asJson)
      val target = _target.copy(source = source, limitations = A.historicalLimitations)
      When("the closed historical target and independent answer are decoded")
      val decoded = target.asJson.as[A.Target]
      Then("historical association remains explicitly adopted rather than mechanically proved")
      decoded shouldBe Right(target)
      A.choices(target) shouldBe Vector(A.historicalChoice, "Defer")
      A.bindingC("fixture-component", target, _proposal, "AdoptRepairPlan", "answer", "participant", _direction.reference).isSuccess shouldBe false
      A.bindingC("fixture-component", target, _proposal, A.historicalChoice, "answer", "participant", _direction.reference).isSuccess shouldBe true
      val malformed = Vector(source.mapObject(_.add("recovery", recovery.asJson.mapObject(_.add("extra", Json.True)))),
        source.mapObject(_.add("resultDisposition", "passed".asJson)),
        source.mapObject(_.add("priorInvocation", snapshot.asJson.mapObject(_.add("proofScope", "native-causality-proved".asJson)))))
      malformed.foreach(value => target.copy(source = value).asJson.as[A.Target].isLeft shouldBe true)
    }
    "retain the whole accepted prefix full process plan immutable limitations and independently bound exact proposal" in {
      Given("one complete actual-shaped output-limit target and one appended dependent Step")
      When("new target Direction and evidence codecs roundtrip")
      val wire = _target.asJson
      val binding = take(A.bindingC("fixture-component", _target, _proposal, _direction.choice,
        _direction.rationale, _direction.participant, _direction.reference))
      Then("all retained fields and source-specific choices remain exact")
      wire.as[A.Target] shouldBe Right(_target)
      A.codec.decode(A.codec.encode(_direction)) shouldBe Right(_direction)
      A.selectBinding(binding) shouldBe Right(binding)
      A.choices(_target) shouldBe Vector("AdoptRepairPlan", "Defer")
      _target.completed.map(_.step) shouldBe _plan.steps.map(_.identity)
      _target.captured.plan.steps.take(_plan.steps.size) shouldBe _plan.steps
      _target.captured.plan.repairLimit shouldBe _plan.repairLimit
    }
    "refuse missing null extended source fields crossed limitations outcomes proposals and source-specific choices" in {
      Given("independent mutations of every newly closed boundary")
      val wire = _target.asJson
      val invalid = Vector(wire.mapObject(_.remove("processPlan")), wire.mapObject(_.add("extra", Json.True)),
        wire.mapObject(_.add("closurePolicy", Json.Null)), wire.mapObject(_.add("captured", Json.Null)),
        wire.mapObject(_.add("limitations", A.historicalLimitations)),
        wire.mapObject(_.add("source", _source.mapObject(_.add("extra", Json.True)))),
        wire.mapObject(_.add("source", _source.mapObject(_.add("termination", "termination-unconfirmed".asJson)))),
        wire.mapObject(_.add("source", _source.mapObject(_.add("outcome", "passed".asJson)))),
        wire.mapObject(_.add("source", _source.mapObject(_.add("result", Json.Null)))),
        wire.mapObject(_.add("source", _source.mapObject(_.add("base", 1.asJson)))),
        wire.mapObject(_.add("source", _source.mapObject(_.add("sourceRevision", "01".asJson)))),
        wire.mapObject(_.add("source", _source.mapObject(_.add("checkIdentity", "".asJson)))),
        _target.copy(completed = Vector.empty).asJson, _target.copy(generation = Long.MaxValue).asJson)
      When("the new target decoder and exact independent binding reader inspect them")
      val refused = invalid.map(_.as[A.Target])
      Then("none becomes a repair authorization or legacy fallback")
      refused.foreach(_.isLeft shouldBe true)
      A.bindingC("fixture-component", _target, _proposal.copy(revision = "other"), "AdoptRepairPlan", "answer", "participant", _direction.reference).isSuccess shouldBe false
      A.bindingC("fixture-component", _target, _proposal, A.historicalChoice, "answer", "participant", _direction.reference).isSuccess shouldBe false
      A.bindingC("fixture-component", _target, _proposal, "AdoptRepairPlan", "answer", "foreign", _direction.reference).isSuccess shouldBe false
      A.codec.decode(A.codec.encode(_direction.copy(choice = "Defer"))).isLeft shouldBe true
      val binding = take(A.bindingC("fixture-component", _target, _proposal, "Defer", "Keep waiting", "participant", _direction.reference))
      A.selectBinding(binding.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
    }
    "preserve all accepted commits base generation and counters while appending exactly one dependent corrective Step" in {
      Given("the ordinary ordered controller after one independently accepted original Step")
      val initial = take(O.startC(_plan, _base))
      val implemented = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "implemented")))
      val observed = take(implemented.acceptC(O.CandidateObserved(O.Snapshot(_base.head, _commit.tree))))
      val checked = take(observed.acceptC(O.ChecksObserved(O.Snapshot(_base.head, _commit.tree), Vector("test"), "check", true)))
      val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key,
        O.Snapshot(_base.head, _commit.tree), "check", "review", O.ReviewOutcome.Accept)))
      val closing = take(reviewed.acceptC(O.ClosureIntentSaved(O.Snapshot(_base.head, _commit.tree), "authority")))
      val closed = take(closing.acceptC(O.StepCommitObserved("authority", _commit)))
      val phase = take(closed.acceptC(O.CandidateObserved(O.Snapshot(_commit.identity, _commit.tree))))
      When("historical eligibility appends one Step without executing it")
      val adopted = take(phase.adoptValidationRepairRevisionC(_proposed, "independent-human"))
      Then("the accepted prefix counters and original base remain unchanged and only generation advances")
      adopted.stage shouldBe O.Stage.Implementation
      adopted.base shouldBe phase.base
      adopted.completed shouldBe phase.completed
      adopted.repairs shouldBe phase.repairs
      adopted.ordinaryRepairs shouldBe phase.ordinaryRepairs
      adopted.generation shouldBe phase.generation + 1
      val incomplete = take(phase.acceptC(O.IncompleteChecksObserved(O.Snapshot(_commit.identity, _commit.tree),
        Vector("test"), "actual-output-limit-receipt")))
      incomplete.stage shouldBe O.Stage.Decision
      incomplete.decisionSource shouldBe Some(O.Stage.PhaseValidation)
      take(incomplete.adoptValidationRepairRevisionC(_proposed, "independent-human")).completed shouldBe phase.completed
      Vector(_proposed.copy(steps = _proposed.steps.reverse), _proposed.copy(repairLimit = 3),
        _proposed.copy(phaseChecks = Vector("compile")), _proposed.copy(steps = _proposed.steps.updated(1, _proposed.steps.last.copy(dependencies = Vector.empty))))
        .foreach(plan => phase.adoptValidationRepairRevisionC(plan, "independent-human").isSuccess shouldBe false)
    }
    "prepare only the exact independently supplied answer with matching target reference participant and limitations" in {
      Given("a complete passive description and specification-owned independent answer")
      val description = V.GoalPhaseValidationRepairDescriptionResult("fixture-session", "adoption-required", A.message,
        "fixture-component", A.targetRecord(_target), A.choices(_target), A.adoptionOperation)
      val reference = V.WorkflowArtifactReference("human", "1")
      val answer = V.GoalPhaseValidationRepairAdoptionInput("fixture-session", description.target,
        GoalPhasePlanRevision.publicProposal(Some(_proposal)).get, "AdoptRepairPlan", "Exact independent answer", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      def _prepare_(input: V.GoalPhaseValidationRepairAdoptionInput = answer,
        worker: V.WorkflowExecutionEvidence = execution) = NativeValidationRepairPreparation.prepareC(
        description, input, reference, worker, "participant", "independent-issuer", Instant.parse("2026-10-24T00:00:00Z"))
      When("the passive adapter serializes that answer and independently crossed alternatives")
      val exact = _prepare_()
      val refused = Vector(_prepare_(answer.copy(sessionId = "foreign")), _prepare_(answer.copy(choice = A.historicalChoice)),
        _prepare_(answer.copy(reference = reference.copy(revision = "2"))), _prepare_(worker = execution.copy(workerIdentity = Some("foreign"))),
        _prepare_(answer.copy(target = A.targetRecord(_target.copy(limitations = A.historicalLimitations)))))
      Then("only the exact binding can be provisioned and preparation supplies no native execution or new authority")
      exact.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      _prepare_(answer.copy(choice = "Defer")).isSuccess shouldBe true
    }
  }
}
