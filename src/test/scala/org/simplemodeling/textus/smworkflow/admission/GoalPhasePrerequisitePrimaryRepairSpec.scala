package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Printer}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhasePrerequisitePrimaryRepair as A
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeWork as W
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Pure business fixtures do not provision evidence, create native history or
  * claim that a specification process is a real Human/publication receipt. */
private[admission] object PrimaryPublicationRepairSpecificationFixture {
  def sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  val base = O.Snapshot("a" * 40, "b" * 40)
  val snapshot = O.Snapshot(base.head, "c" * 40)
  val plan = O.Plan("primary-plan", "1", "PRIMARY", "primary-repository",
    Vector(O.Step("primary-step", "Correct selected primary behavior", Vector("src/primary.scala", "src/extra.scala", "docs/preserved.md"),
      Vector.empty, Vector("primary-test"))), Vector("primary-test"))
  val candidate = C.Payload("10", plan.steps.head.identity, Vector("src/primary.scala"), Vector("src/primary.scala"),
    Some(snapshot), Vector("src/primary.scala"), Some("diff --git a/src/primary.scala b/src/primary.scala\n+implemented\n"))
  val target = LocalPublication.Target("primary-delivery", "primary-component", "primary-workspace", beforeChecks = true)
  val source = LocalPublication.Source(snapshot.head, snapshot.tree, Vector(LocalPublication.Change("src/primary.scala", "M", " ")))
  val program = "primary-publication-program"
  val output = "[info] compiling 1 Scala source\n[error] src/primary.scala:1: missing primaryApi\n" +
    "[error] (Compile / compileIncremental) Compilation failed\n"
  def process(text: String = output, termination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(1),
    truncated: Boolean = false, identity: String = program): Json = {
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    WorkflowSbtCapture.encode(ProcessExecutionResult(termination, ProcessExecutionCapture(bytes, bytes.size.toLong, truncated),
      ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 66358L, identity))
  }
  def processPolicy(target: LocalPublication.Target): String = sorted(Json.obj("processPlan" -> Json.obj("sbtPrograms" ->
    Json.arr(Json.obj("identity" -> program.asJson, "target" -> target.workspace.asJson, "operation" -> "publish-local".asJson)))))
  def publicationPolicy(targets: Vector[LocalPublication.Target]): String = sorted(Json.obj(
    "storeName" -> "fixture-store".asJson, "collection" -> "fixture-publication".asJson, "targets" -> targets.asJson,
    "catalogOutputs" -> Json.arr()))
  def result(candidate: C.Payload = PrimaryPublicationRepairSpecificationFixture.candidate,
    target: LocalPublication.Target = PrimaryPublicationRepairSpecificationFixture.target,
    source: LocalPublication.Source = PrimaryPublicationRepairSpecificationFixture.source): LocalPublication.Result = {
    val input = LocalPublicationRuntime.prerequisiteInput("primary-session", candidate, target)
    LocalPublication.Result("failed", "Specification terminated Compile failure",
      LocalPublication.Request(input.copy(requestIdentity = input.requestIdentity + ":prerequisite:0"), target, source,
        "primary-participant", "Specification runtime publication", None, Vector.empty), Some(process()))
  }
  def validation(plan: O.Plan = PrimaryPublicationRepairSpecificationFixture.plan): O.State = {
    val initial = take(O.startC(plan, base, Some(GoalPhaseRuntimeRepairControl.version)))
    val semantic = initial.next.asInstanceOf[O.Semantic]
    take(take(initial.acceptC(O.SemanticCompleted(semantic.key, "Selected source implemented")))
      .acceptC(O.CandidateObserved(snapshot)))
  }
  val controller = validation()
  val excluded = Vector(G.DirtyPath('?', '?', "docs/preserved.md", None))
  val failure = A.Failure("primary-instance", "primary-participant", "primary-session", "12", sorted(B.controller(controller)),
    plan, base, 1L, Vector.empty, GoalPhaseRepairProgress.snapshot(controller), "12", candidate,
    ContextReference("accepted-primary-result", "1"), Vector(target), publicationPolicy(Vector(target)), processPolicy(target),
    result(), None, Vector.empty, excluded)
  val repair = take(A.stateC(controller, failure))
  val fact = A.Fact(A.schema, failure, A.directive(failure), sorted(B.controller(repair)))
  val reuse = A.CaptureReuse(ContextReference("primary-session", "13"), "12", "10", 1L)
  val repairedCandidate = candidate.copy(sourceRevision = "15", candidate = Some(snapshot.copy(tree = "d" * 40)),
    diff = Some("Actual corrected primary diff"), primaryPublicationRepair = Some(reuse))
  val context = W.PublicationFailureContext(reuse.repair, candidate.changedPaths, candidate.diff.get, failure.failed, testsExecuted = false)
  val request = W.Request("primary-session", "13", "RepairStep", Some(plan.steps.head.identity), 2L,
    plan.steps.head.purpose, plan, base, Vector.empty, None, None, Some(fact.directive),
    repairProgress = Some(GoalPhaseRepairProgress.snapshot(repair)), publicationFailure = Some(context))
  def failureAt(controller: O.State, revision: String): A.Failure = {
    val selected = candidate.copy(sourceRevision = (BigInt(revision) - 2).toString, candidate = controller.candidate)
    val actualsource = source.copy(head = controller.candidate.get.head, tree = controller.candidate.get.tree)
    failure.copy(frontierRevision = revision, controller = sorted(B.controller(controller)), plan = controller.plan,
      base = controller.base, generation = controller.generation, completed = controller.completed,
      repairProgress = GoalPhaseRepairProgress.snapshot(controller), candidateRecordRevision = revision, candidate = selected,
      failed = result(selected, source = actualsource))
  }
  def nextValidation(state: O.State, candidate: O.Snapshot = PrimaryPublicationRepairSpecificationFixture.snapshot): O.State = {
    val semantic = state.next.asInstanceOf[O.Semantic]
    take(take(state.acceptC(O.SemanticCompleted(semantic.key, "Assigned primary correction completed")))
      .acceptC(O.CandidateObserved(candidate)))
  }

  import PrimaryPublicationContinuationSpecificationFixture as P
  import DependencyValidationSpecificationFixture as D
  private val _narrow_initial = take(O.startC(D.plan, D.primaryBase, Some(GoalPhaseRuntimeRepairControl.version)))
  private val _narrow_semantic = _narrow_initial.next.asInstanceOf[O.Semantic]
  private val _narrow_validation = take(take(_narrow_initial.acceptC(O.SemanticCompleted(_narrow_semantic.key,
    "Original selected primary source"))).acceptC(O.CandidateObserved(P.currentCandidate.candidate.get)))
  val narrowController = take(_narrow_validation.adoptPrerequisiteSourceRevisionC(P.sourceDirection.directive))
  private val _narrow_program = io.circe.parser.parse(P.processPolicy).toOption.get.mapObject { root =>
    val body = root("processPlan").get.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(
      fields("sbtPrograms").get.asArray.get.map(_.mapObject(_.add("identity", program.asJson))))))
    root.add("processPlan", body)
  }
  private val _narrow_input = LocalPublicationRuntime.prerequisiteInput("same-session", P.currentCandidate, P.primary)
  private val _narrow_result = LocalPublication.Result("failed", "Specification later primary Compile failure",
    LocalPublication.Request(_narrow_input.copy(requestIdentity = _narrow_input.requestIdentity + ":primary:same-session:35"),
      P.primary, P.primarySource, "participant", "Specification one-shot source publication", None, Vector.empty), Some(process()))
  val narrowFailure = A.Failure("fixture-component", "participant", "same-session", "36", sorted(B.controller(narrowController)),
    D.plan, D.primaryBase, 2L, Vector.empty, GoalPhaseRepairProgress.snapshot(narrowController), "33", P.currentCandidate,
    P.primaryTarget.requestingResult, Vector(P.dependency, P.primary), publicationPolicy(Vector(P.dependency, P.primary)),
    sorted(_narrow_program), _narrow_result, None, Vector(P.artifactFact, P.primaryFact), excluded)
  val narrowRepair = take(A.stateC(narrowController, narrowFailure))
  val narrowReuse = A.CaptureReuse(ContextReference("same-session", "37"), "33", "27", 2L)
  val narrowCandidate = P.currentCandidate.copy(sourceRevision = "40", candidate = Some(P.currentCandidate.candidate.get.copy(tree = "e" * 40)),
    diff = Some("Corrected primary source"), primaryPublicationRepair = Some(narrowReuse))
  val narrowFact = A.Fact(A.schema, narrowFailure, A.directive(narrowFailure), sorted(B.controller(narrowRepair)))
  val narrowValidation = nextValidation(narrowRepair, narrowCandidate.candidate.get)
  val narrowTarget = A.Target(A.targetSchema, narrowFailure.componentInstance, narrowFailure.participant,
    narrowFailure.sessionId, "42", sorted(B.controller(narrowValidation)), narrowValidation.generation, "42",
    narrowCandidate, narrowFailure, Some(narrowReuse.repair), A.scope(narrowFailure), "new-primary-publication")
  val narrowOnce = A.Direction(narrowTarget, "AuthorizePrimaryPublicationOnce", "SPECIFICATION-OWNED exact publication",
    ContextReference("human-once", "1"))
  val narrowBroad = narrowOnce.copy(choice = "AuthorizePrimaryPublicationAndSameScopeRepairs",
    reference = ContextReference("human-scope", "1"))
  def primaryValidation(passed: Boolean): GoalPhaseNativeValidation.Payload = {
    val check = GoalPhaseNativeValidation.Check("primary-test", 1)
    val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(if (passed) 0 else 1),
      if (passed) "SPECIFICATION-OWNED passed selected test" else "SPECIFICATION-OWNED failed selected test", "",
      Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test,
        if (passed) WorkflowSbtValidation.Outcome.Succeeded else WorkflowSbtValidation.Outcome.Failed, None, None)))
    GoalPhaseNativeValidation.Payload("44", narrowCandidate.step, narrowCandidate.candidate.get, Vector(check), Vector(receipt),
      Some(if (passed) "passed" else "repair-required"))
  }
  final class PublicationMemory extends LocalPublication.Port {
    private val _rows = scala.collection.mutable.Map.empty[String, Json]
    private var _identities = 0
    var source = PrimaryPublicationRepairSpecificationFixture.source
    var exit = 1
    var starts = 0
    def loadC(key: String) = Consequence.success(_rows.get(key))
    def createC(key: String, value: Json): Consequence[Unit] =
      if (_rows.contains(key)) Consequence.stateConflict("Specification immutable duplicate")
      else { _rows(key) = value; Consequence.unit }
    def sourceC(target: LocalPublication.Target) = Consequence.success(source)
    def executeC(target: LocalPublication.Target) = {
      starts += 1
      Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(target.workspace))),
        SbtOperation.PublishLocal, WorkflowSbtCapture.decode(process(termination = ProcessExecutionTermination.Exited(exit))).toOption.get)))
    }
    def newIdentity() = { _identities += 1; "specification-publication-" + _identities }
  }
  /** Uses the real immutable prerequisite/successor algorithms with an in-memory
    * Port. These process facts are specification data, never runtime evidence. */
  def publishedPrimary(): (PublicationMemory, LocalPublication.Result, LocalPublication.ConfirmedFailureSuccessor) = {
    val memory = new PublicationMemory
    val failed = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput("same-session",
      narrowFailure.candidate, P.primary), P.primary, "participant", memory))
    memory.source = source.copy(head = narrowCandidate.candidate.get.head, tree = narrowCandidate.candidate.get.tree)
    memory.exit = 0
    val permit = LocalPublication.ConfirmedFailureSuccessor(narrowReuse.repair, failed, memory.source,
      ContextReference("same-session", "42"), narrowFailure.processPolicy, narrowFailure.publicationPolicy)
    val published = take(LocalPublication.ensureConfirmedFailureSuccessorC(LocalPublicationRuntime.primaryRepairInput("same-session",
      narrowCandidate, P.primary, permit), P.primary, "participant", permit, memory))
    (memory, published, permit)
  }
}

/** Primary publication repair authority, attribution and retained capture contracts.
  * @since   Nov.  2, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePrerequisitePrimaryRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PrimaryPublicationRepairSpecificationFixture as F

  "Primary publication Compile classification" should {
    "accept complete terminated Compile output at the exact primary target source participant and program" in {
      Given("the canonical failed primary publication with complete stdout/stderr and its fixed host program")
      val failed = F.failure.failed
      When("the primary compile classifier evaluates the retained process")
      val eligible = A.compileFailure(failed, F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target))
      Then("the known Compile failure qualifies while its actual output stays unchanged")
      eligible shouldBe true
      A.compileFailure(failed.copy(process = Some(F.process(F.output + "[error] Total time: 66 s, completed Nov 2, 2026\n"))),
        F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)) shouldBe true
      WorkflowSbtCapture.text(WorkflowSbtCapture.decode(failed.process.get).toOption.get.stdout).value shouldBe F.output
      A.failureValid(F.failure) shouldBe true
    }

    "accept the ordinary parallel Compile and Scaladoc failure while refusing doc-only or other failed tasks" in {
      Given("complete publishLocal output with the authoritative Compile failure and its parallel Scaladoc failure")
      val doc = "[error] (Compile / doc) Scaladoc generation failed\n"
      val footer = "[error] Total time: 66 s, completed Nov 2, 2026\n"
      val parallel = F.output + doc + footer
      val outputs = Vector(parallel, doc + footer,
        F.output + "[error] (publishLocal) repository failed\n" + footer,
        "[error] (Compile / resources) resource generation failed\n" + parallel,
        parallel.stripSuffix(footer) + "[error] unexpected later error\n" + footer)
      When("the classifier requires every task summary and the final error to belong to the two exact allowed Compile failures")
      val classifications = outputs.map(output => A.compileFailure(F.failure.failed.copy(process = Some(F.process(output))),
        F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("only the complete Compile plus parallel Scaladoc failure qualifies and its retained output stays intact")
      classifications shouldBe Vector(true, false, false, false, false)
      val captured = F.failure.failed.copy(process = Some(F.process(parallel)))
      WorkflowSbtCapture.text(WorkflowSbtCapture.decode(captured.process.get).toOption.get.stdout).value shouldBe parallel
    }

    "refuse nonstart UNKNOWN truncation arbitrary exits Test failures and late publication errors" in {
      Given("distinct retained outcomes whose bytes do not prove the exact completed Compile failure")
      val failed = F.failure.failed
      val invalid = Vector(failed.copy(status = "uncertain"), failed.copy(status = "admission-rejected", process = None),
        failed.copy(process = Some(F.process(termination = ProcessExecutionTermination.NotStarted))),
        failed.copy(process = Some(F.process(termination = ProcessExecutionTermination.TerminationUnconfirmed))),
        failed.copy(process = Some(F.process(termination = ProcessExecutionTermination.TimedOut))),
        failed.copy(process = Some(F.process(truncated = true))),
        failed.copy(process = Some(F.process("[error] ordinary task failed\n"))),
        failed.copy(process = Some(F.process("[error] (Test / test) Tests unsuccessful\n"))),
        failed.copy(process = Some(F.process(F.output + "[error] (publishLocal) repository failed\n"))),
        failed.copy(process = Some(F.process("[info] published artifact\n" + F.output))))
      When("each result is classified under the same exact target and source")
      val classifications = invalid.map(A.compileFailure(_, F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("none grants source repair or a blind rerun")
      classifications shouldBe Vector.fill(invalid.size)(false)
    }

    "refuse crossed targets components workspaces sessions sources participants and programs" in {
      Given("a real Compile diagnostic and otherwise plausible crossed bindings")
      val failed = F.failure.failed
      val targets = Vector(F.target.copy(identity = "other-target"), F.target.copy(component = "other-component"),
        F.target.copy(workspace = "other-workspace"), F.target.copy(dependency = Some("dependency")), F.target.copy(beforeChecks = false))
      When("the exact bindings and fixed program are checked")
      val targetresults = targets.map(A.compileFailure(failed, _, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      val otherresults = Vector(
        A.compileFailure(failed, F.target, "other-session", "primary-participant", F.source, F.processPolicy(F.target)),
        A.compileFailure(failed, F.target, "primary-session", "other-participant", F.source, F.processPolicy(F.target)),
        A.compileFailure(failed, F.target, "primary-session", "primary-participant", F.source.copy(tree = "e" * 40), F.processPolicy(F.target)),
        A.compileFailure(failed.copy(process = Some(F.process(identity = "other-program"))), F.target,
          "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("every crossed binding is ineligible")
      (targetresults ++ otherresults).forall(_ == false) shouldBe true
      A.failureValid(F.failure.copy(candidate = F.candidate.copy(sourceRevision = "9"))) shouldBe false
    }

    "require the check selection and result namespace to remain wholly unstarted" in {
      Given("each bounded check or metadata slot can independently retain an actual record")
      val slots = Vector("selection", "validation", "check-intent-0", "check-result-0", "check-recovery-0", "check-intent-15")
      When("the existing passive namespace gate observes each occupied slot")
      val occupied = slots.map(slot => GoalPhaseNativeValidation.requireUnstartedPrerequisiteC(
        key => org.goldenport.Consequence.success(key == slot)).isSuccess)
      Then("all occupied namespaces refuse, while a genuinely empty namespace remains eligible")
      occupied shouldBe Vector.fill(slots.size)(false)
      GoalPhaseNativeValidation.requireUnstartedPrerequisiteC(_ => org.goldenport.Consequence.success(false)).isSuccess shouldBe true
    }
  }

  "Ordinary primary repair accounting and retained codecs" should {
    "reconstruct the same one reserved generation and actual publication context without a validation receipt" in {
      Given("one original Validation controller and its immutable primary repair fact")
      val wire = A.codec.encode(F.fact)
      When("repeated collection decodes the fact and reconstructs from the same adjacent predecessor")
      val restored = Vector.fill(3)(A.codec.decode(wire).toOption.get).map(f => take(A.stateC(F.controller, f.failure)))
      val request = W.inputCodec.decode(W.inputCodec.encode(F.request)).toOption.get
      Then("each reconstruction reports one consumed ordinary batch and the original controller is unchanged")
      restored.map(s => (s.generation, s.repairs, s.ordinaryRepairs)) shouldBe Vector.fill(3)((2L, 1, 1))
      F.controller.repairs shouldBe 0
      restored.foreach { state => state.candidate shouldBe None; state.checksReceipt shouldBe None; state.reviewEvidence shouldBe None }
      request.publicationFailure shouldBe Some(F.context)
      request.publicationFailure.get.testsExecuted shouldBe false
      request.checkFailure shouldBe None
      request.review shouldBe None
      request.candidate shouldBe None
      A.currentCauseAt(F.fact, F.repair) shouldBe true
      take(A.workContextC(F.fact, F.reuse.repair, F.repair, "RepairStep")) shouldBe Some(F.context)
      W.admitSemanticResultC(request, W.Result("completed", "Corrected the selected source", Vector.empty)).isSuccess shouldBe true
      A.codec.encode(A.codec.decode(wire).toOption.get) shouldBe wire
      A.codec.decode(wire.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      F.failure.asJson.as[A.Failure] shouldBe Right(F.failure)
      F.failure.asJson.hcursor.downField("artifactAdoption").succeeded shouldBe false
      F.failure.asJson.mapObject(_.add("artifactAdoption", Json.Null)).as[A.Failure].isLeft shouldBe true
      F.failure.asJson.mapObject(_.add("undeclared", Json.True)).as[A.Failure].isLeft shouldBe true
    }

    "use the existing selected limit trend stop and runtime ceiling without granting free repairs" in {
      Given("ordinary runtime repair policy with a one-batch window and with the finite runtime ceiling")
      val directive = O.RepairDirective("Actual primary Compile defect", Vector("same-primary-defect"))
      val first = take(F.validation(F.plan.copy(repairLimit = 1)).continuePrimaryPublicationRepairC("failed:1", directive))
      When("another failure exhausts the window and recurring failures reach the existing trend stop")
      val limited = take(F.nextValidation(first).continuePrimaryPublicationRepairC("failed:2", directive))
      val stalled = (1 to 4).foldLeft(F.validation()) { (state, index) =>
        val current = if (state.stage == O.Stage.Repair) F.nextValidation(state) else state
        take(current.continuePrimaryPublicationRepairC("recurrence:" + index, directive))
      }
      val capped = (1 to 11).foldLeft(F.validation(F.plan.copy(repairLimit = 50))) { (state, index) =>
        val current = if (state.stage == O.Stage.Repair) F.nextValidation(state) else state
        take(current.continuePrimaryPublicationRepairC("distinct:" + index,
          directive.copy(findings = Vector("distinct-defect-" + index))))
      }
      Then("the original Decision/ContinueRepairs path retains consumed counts and no extra generation is issued")
      (limited.stage, limited.repairs, limited.ordinaryRepairs, limited.generation) shouldBe ((O.Stage.Decision, 1, 1, 2L))
      limited.pendingRepair shouldBe Some(directive)
      (stalled.stage, stalled.repairs, stalled.generation) shouldBe ((O.Stage.Decision, 3, 4L))
      stalled.decision.get should include("recurrence/stall")
      (capped.stage, capped.repairs, capped.ordinaryRepairs) shouldBe ((O.Stage.Decision, 10, 10))
    }

    "retain the primary work cause through the existing limit and trend repair continuations" in {
      val scenarios = Vector(("selected limit", F.plan.copy(repairLimit = 2), 2),
        ("recurrence/stall", F.plan.copy(repairLimit = 10), 3))
      scenarios.foreach { (reason, plan, consumed) =>
        Given("ordinary primary repairs have consumed the selected window or reached the existing " + reason + " stop")
        val frontier = (1 to consumed).foldLeft(F.validation(plan)) { (current, index) =>
          F.nextValidation(take(A.stateC(current, F.failureAt(current, (index * 10 + 2).toString))))
        }
        val failure = F.failureAt(frontier, "72")
        val decision = take(A.stateC(frontier, failure))
        val fact = A.Fact(A.schema, failure, A.directive(failure), F.sorted(B.controller(decision)))
        val retained = A.codec.decode(A.codec.encode(fact)).toOption.get
        val reference = ContextReference("primary-session", "73")
        When("the controller receives the existing native-admitted repair continuation event")
        // This is specification-owned controller input; the native Human owner
        // still authenticates real independent evidence before emitting it.
        val continued = take(decision.acceptC(O.RepairContinuationAdmitted("specification-admitted-continue-repairs")))
        val recollected = take(take(A.stateC(frontier, retained.failure))
          .acceptC(O.RepairContinuationAdmitted("specification-admitted-continue-repairs")))
        val publication = take(A.workContextC(retained, reference, continued, "RepairStep"))
        val request = F.request.copy(sourceRevision = "74", selectedPlan = continued.plan,
          originalBase = continued.base, completed = continued.completed, generation = continued.generation,
          repairDirective = continued.repairDirective, repairProgress = Some(GoalPhaseRepairProgress.snapshot(continued)),
          publicationFailure = publication)
        Then("the renewed window reserves one batch and issues its real publication cause without any check or review context")
        decision.stage shouldBe O.Stage.Decision
        decision.repairs shouldBe consumed
        decision.ordinaryRepairs shouldBe consumed
        decision.generation shouldBe failure.generation
        GoalPhaseOrderedHumanDecision.choicesFor(decision) shouldBe Vector("ContinueRepairs", "Defer", "Cancel")
        if (reason == "recurrence/stall") decision.decision.get should include(reason)
        (continued.stage, continued.generation, continued.repairs, continued.ordinaryRepairs) shouldBe
          ((O.Stage.Repair, failure.generation + 1, 1, 1))
        continued.repairObservations shouldBe empty
        continued.pendingRepair shouldBe None
        continued.repairDirective shouldBe Some(fact.directive)
        F.sorted(B.controller(recollected)) shouldBe F.sorted(B.controller(continued))
        continued.acceptC(O.RepairContinuationAdmitted("specification-repeated-answer")).isSuccess shouldBe false
        A.currentCauseAt(retained, decision) shouldBe false
        A.currentCauseAt(retained, continued) shouldBe true
        request.publicationFailure.get.publication shouldBe failure.failed
        request.publicationFailure.get.testsExecuted shouldBe false
        request.checkFailure shouldBe None
        request.review shouldBe None
        W.inputCodec.decode(W.inputCodec.encode(request)) shouldBe Right(request)
        W.admitSemanticResultC(request, W.Result("completed", "Corrected the continued primary Compile failure", Vector.empty)).isSuccess shouldBe true
        When("the continued repair produces its candidate and later actual checks or review require another ordinary repair")
        val snapshot = F.snapshot.copy(tree = "e" * 40)
        val validation = F.nextValidation(continued, snapshot)
        val candidate = failure.candidate.copy(sourceRevision = "76", candidate = Some(snapshot),
          diff = Some("Corrected selected primary source after the renewed repair window"), primaryPublicationRepair = Some(
          A.CaptureReuse(reference, failure.candidateRecordRevision, failure.candidate.sourceRevision, failure.generation)))
        val directive = O.RepairDirective("Later actual check or independent review defect", Vector("New ordinary finding"))
        val checkrepair = take(validation.acceptC(O.RepairableChecksObserved(snapshot, plan.steps.head.checks,
          "specification-later-failed-check", directive)))
        val reviewed = take(validation.acceptC(O.ChecksObserved(snapshot, plan.steps.head.checks,
          "specification-later-passed-check", true)))
        val reviewrepair = take(reviewed.acceptC(O.ReviewAdmitted(reviewed.next.asInstanceOf[O.Semantic].key,
          snapshot, "specification-later-passed-check", "specification-later-independent-review", O.ReviewOutcome.Repair, Some(directive))))
        Then("the initial continued candidate uses the failed successor while later work keeps its own cause")
        A.publicationRoute(failure, A.scope(failure), Vector.empty, candidate, "78", validation.generation) shouldBe
          A.PublicationRoute.FirstFailureSuccessor
        Vector(checkrepair, reviewrepair).foreach { later =>
          later.stage shouldBe O.Stage.Repair
          later.generation shouldBe failure.generation + 2
          later.repairs shouldBe 2
          later.ordinaryRepairs shouldBe 2
          A.currentCauseAt(retained, later) shouldBe false
          take(A.workContextC(retained, reference, later, "RepairStep")) shouldBe None
        }
      }
    }

    "preserve legacy canonical requests primary targets and dependency captures without new optional fields" in {
      Given("old v2 primary continuation fixtures and a legacy request without publication failure context")
      import PrimaryPublicationContinuationSpecificationFixture as P
      val request = F.request.copy(kind = "ImplementStep", generation = 1L, repairDirective = None,
        repairProgress = Some(GoalPhaseRepairProgress.snapshot(F.controller)), publicationFailure = None)
      val wire = W.inputCodec.encode(request)
      When("the existing owners read and re-encode the legacy values")
      val restored = W.inputCodec.decode(wire)
      val candidate = C.codec.decode(C.codec.encode(P.currentCandidate))
      Then("old wire versions and original source capture bytes remain exact")
      wire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v2")
      wire.hcursor.downField("payload").downField("publicationFailure").focus shouldBe None
      W.inputCodec.encode(restored.toOption.get) shouldBe wire
      candidate shouldBe Right(P.currentCandidate)
      candidate.toOption.get.dependencySourceCapture shouldBe Some(P.capture)
      P.primaryTarget.asJson.as[B.PrimaryTarget] shouldBe Right(P.primaryTarget)
      B.primaryCodec.decode(B.primaryCodec.encode(P.primaryFact)) shouldBe Right(P.primaryFact)
    }
  }

  "Primary capture reuse and publication authority" should {
    "retain a complete dependency capture at its original generation and source revision" in {
      Given("an authenticated completed legacy source capture and a later primary repair reference")
      import PrimaryPublicationContinuationSpecificationFixture as P
      val reuse = A.CaptureReuse(ContextReference("same-session", "40"), "35", P.currentCandidate.sourceRevision, 2L)
      val candidate = P.currentCandidate.copy(sourceRevision = "42", diff = Some("Corrected primary source only"),
        primaryPublicationRepair = Some(reuse))
      When("the versioned repaired candidate is encoded and decoded")
      val decoded = C.codec.decode(C.codec.encode(candidate))
      Then("the continuation is explicit and the dependency is not recaptured or relabeled")
      decoded shouldBe Right(candidate)
      decoded.toOption.get.dependencySourceCapture shouldBe Some(P.capture)
      decoded.toOption.get.dependencySourceCapture.get.sourceRevision shouldBe P.capture.sourceRevision
      decoded.toOption.get.dependencySourceCapture.get.generation shouldBe P.capture.generation
      C.sourceCaptureValid(candidate.copy(primaryPublicationRepair = None)) shouldBe false
      C.sourceCaptureValid(candidate.copy(primaryPublicationRepair = Some(reuse.copy(sourceRevision = "28")))) shouldBe false
    }

    "distinguish ordinary coverage from narrow one-shot direction and independently admitted same-scope continuation" in {
      Given("ordinary covering policy plus an explicit legacy one-shot source lineage")
      import PrimaryPublicationContinuationSpecificationFixture as P
      val narrow = F.narrowFailure
      val scope = A.scope(narrow)
      val current = F.nextValidation(F.narrowRepair, F.narrowCandidate.candidate.get)
      val target = A.Target(A.targetSchema, narrow.componentInstance, narrow.participant, narrow.sessionId, "42",
        F.sorted(B.controller(current)), 3L, "42", F.narrowCandidate, narrow, Some(F.narrowReuse.repair), scope, "new-primary-publication")
      val once = A.Direction(target, "AuthorizePrimaryPublicationOnce", "SPECIFICATION-OWNED exact publication", ContextReference("human-once", "1"))
      val broad = once.copy(choice = "AuthorizePrimaryPublicationAndSameScopeRepairs", reference = ContextReference("human-scope", "1"))
      val later = F.narrowCandidate.copy(sourceRevision = "44", diff = Some("Further owned source correction"))
      When("coverage is evaluated against each actual candidate and complete fixed scope")
      val ordinary = A.authorityCovers(F.failure, A.scope(F.failure), Vector.empty, F.repairedCandidate, "17")
      val uncovered = A.authorityCovers(narrow, scope, Vector.empty, F.narrowCandidate, "42")
      val exact = A.authorityCovers(narrow, scope, Vector(once), F.narrowCandidate, "42")
      val lateronce = A.authorityCovers(narrow, scope, Vector(once), later, "45")
      val laterbroad = A.authorityCovers(narrow, scope, Vector(broad), later, "45")
      Then("ordinary work continues automatically and only genuine narrow coverage needs another direction")
      Vector(ordinary, uncovered, exact, lateronce, laterbroad) shouldBe Vector(true, false, true, false, true)
      A.authorityCovers(narrow, scope.copy(processPolicy = "{\"changedProgram\":1}"), Vector(broad), later, "20") shouldBe false
      A.authorityCovers(narrow, scope.copy(targets = Vector(F.target.copy(workspace = "other-destination"))), Vector(broad), later, "20") shouldBe false
      A.authorityCovers(narrow, scope, Vector(broad), later.copy(changedPaths = Vector("foreign.scala")), "20") shouldBe false
      A.targetC(GoalPhaseNativeMetadata.record(target.asJson)).isSuccess shouldBe true
      B.selectBinding(take(A.bindingC(target.componentInstance, target, broad.choice, broad.rationale,
        target.participant, broad.reference))).isRight shouldBe true
    }
  }

  "Ordinary repairs after corrected primary publication" should {
    "issue the actual failed-check context and use covered ordinary publication for the next candidate" in {
      Given("the corrected primary generation and the retained complete dependency source capture")
      val (memory, published, originalpermit) = F.publishedPrimary()
      val failed = F.primaryValidation(passed = false)
      val first = A.publicationRoute(F.narrowFailure, A.scope(F.narrowFailure), Vector(F.narrowBroad),
        F.narrowCandidate, "42", F.narrowValidation.generation)
      When("the actual native check classifier reserves an ordinary check repair after prerequisite publication")
      val repair = take(GoalPhaseNativeValidation.observedStateC(F.narrowValidation, failed, "actual-shaped-failed-primary-check"))
      val context = take(W.checkFailureContextC(F.narrowCandidate.changedPaths, F.narrowCandidate.diff.get, failed))
      val publication = take(A.workContextC(F.narrowFact, F.narrowReuse.repair, repair, "RepairStep"))
      val request = W.Request("same-session", "46", "RepairStep", Some(F.narrowCandidate.step), repair.generation,
        "Correct the actual selected test failure", repair.plan, repair.base, repair.completed, None, None,
        repair.repairDirective, checkFailure = Some(context), repairProgress = Some(GoalPhaseRepairProgress.snapshot(repair)),
        publicationFailure = publication)
      val next = F.narrowCandidate.copy(sourceRevision = "48", candidate = Some(failed.candidate.copy(tree = "f" * 40)),
        diff = Some("Further owned primary correction after actual test failure"))
      val validation = F.nextValidation(repair, next.candidate.get)
      memory.source = memory.source.copy(tree = next.candidate.get.tree)
      val laterpublication = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput("same-session", next,
        F.narrowFailure.failed.request.target), F.narrowFailure.failed.request.target, "participant", memory))
      Then("the old publication failure does not replace actual executed tests or consume an extra repair")
      first shouldBe A.PublicationRoute.FirstFailureSuccessor
      published.status shouldBe "published"
      laterpublication.status shouldBe "published"
      memory.starts shouldBe 3
      LocalPublication.ensureConfirmedFailureSuccessorC(LocalPublication.Input("stale-check-repair", originalpermit.previous.request.target.identity,
        Some("same-session")), originalpermit.previous.request.target, "participant", originalpermit.copy(source = memory.source), memory).isSuccess shouldBe false
      A.currentCauseAt(F.narrowFact, repair) shouldBe false
      request.publicationFailure shouldBe None
      request.review shouldBe None
      request.checkFailure.get.validation shouldBe failed
      W.admitSemanticResultC(request, W.Result("completed", "Corrected selected failed test", Vector.empty)).isSuccess shouldBe true
      W.inputCodec.decode(W.inputCodec.encode(request)) shouldBe Right(request)
      (repair.generation, repair.repairs, repair.ordinaryRepairs) shouldBe (4L, 2, 2)
      validation.generation shouldBe 4L
      next.dependencySourceCapture shouldBe F.narrowCandidate.dependencySourceCapture
      next.primaryPublicationRepair shouldBe Some(F.narrowReuse)
      C.sourceCaptureValid(next) shouldBe true
      A.publicationRoute(F.narrowFailure, A.scope(F.narrowFailure), Vector(F.narrowBroad), next, "50", validation.generation) shouldBe
        A.PublicationRoute.OrdinaryPrerequisite
      A.publicationRoute(F.narrowFailure, A.scope(F.narrowFailure), Vector(F.narrowOnce), next, "50", validation.generation) shouldBe
        A.PublicationRoute.MissingAuthority
      A.publicationRoute(F.narrowFailure, A.scope(F.narrowFailure).copy(processPolicy = "{}"), Vector(F.narrowBroad),
        next, "50", validation.generation) shouldBe A.PublicationRoute.MissingAuthority
      F.narrowFailure.failed.status shouldBe "failed"
    }

    "retain the independent review repair context and use ordinary publication without replaying the original failed successor" in {
      Given("the corrected primary prerequisite followed by genuinely passed selected-check facts")
      val (memory, published, originalpermit) = F.publishedPrimary()
      val passed = F.primaryValidation(passed = true)
      val reviewed = take(GoalPhaseNativeValidation.observedStateC(F.narrowValidation, passed, "actual-shaped-passed-primary-check"))
      val independent = O.RepairDirective("Correct the independent review finding", Vector("Review found incomplete selected behavior"))
      val review = GoalPhaseNativeReview.Context(F.narrowCandidate.changedPaths, F.narrowCandidate.diff.get, passed.receipts)
      When("the normal independent review admission reserves its own repair generation")
      val repair = take(reviewed.acceptC(O.ReviewAdmitted(reviewed.next.asInstanceOf[O.Semantic].key,
        passed.candidate, "actual-shaped-passed-primary-check", "actual-shaped-independent-review", O.ReviewOutcome.Repair, Some(independent))))
      val publication = take(A.workContextC(F.narrowFact, F.narrowReuse.repair, repair, "RepairStep"))
      val request = W.Request("same-session", "47", "RepairStep", Some(F.narrowCandidate.step), repair.generation,
        "Correct the independent review finding", repair.plan, repair.base, repair.completed, None, None,
        repair.repairDirective, review = Some(review), repairProgress = Some(GoalPhaseRepairProgress.snapshot(repair)),
        publicationFailure = publication)
      val next = F.narrowCandidate.copy(sourceRevision = "49", candidate = Some(passed.candidate.copy(tree = "f" * 40)),
        diff = Some("Further owned primary correction after independent review"))
      val validation = F.nextValidation(repair, next.candidate.get)
      memory.source = memory.source.copy(tree = next.candidate.get.tree)
      val laterpublication = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput("same-session", next,
        F.narrowFailure.failed.request.target), F.narrowFailure.failed.request.target, "participant", memory))
      Then("review suppression uses the current cause and the historical dependency capture remains unchanged")
      A.currentCauseAt(F.narrowFact, repair) shouldBe false
      published.status shouldBe "published"
      laterpublication.status shouldBe "published"
      memory.starts shouldBe 3
      LocalPublication.ensureConfirmedFailureSuccessorC(LocalPublication.Input("stale-review-repair", originalpermit.previous.request.target.identity,
        Some("same-session")), originalpermit.previous.request.target, "participant", originalpermit.copy(source = memory.source), memory).isSuccess shouldBe false
      request.review shouldBe Some(review)
      request.checkFailure shouldBe None
      request.publicationFailure shouldBe None
      request.repairDirective shouldBe Some(independent)
      W.admitSemanticResultC(request, W.Result("completed", "Corrected the independently reviewed finding", Vector.empty)).isSuccess shouldBe true
      W.inputCodec.decode(W.inputCodec.encode(request)) shouldBe Right(request)
      (repair.generation, repair.repairs, repair.ordinaryRepairs) shouldBe (4L, 2, 2)
      next.dependencySourceCapture shouldBe F.narrowCandidate.dependencySourceCapture
      next.primaryPublicationRepair shouldBe Some(F.narrowReuse)
      C.sourceCaptureValid(next) shouldBe true
      A.publicationRoute(F.narrowFailure, A.scope(F.narrowFailure), Vector(F.narrowBroad), next, "51", validation.generation) shouldBe
        A.PublicationRoute.OrdinaryPrerequisite
      A.publicationRoute(F.narrowFailure, A.scope(F.narrowFailure), Vector.empty, next, "51", validation.generation) shouldBe
        A.PublicationRoute.MissingAuthority
      A.publicationRoute(F.narrowFailure, A.scope(F.narrowFailure), Vector(F.narrowOnce), next, "51", validation.generation) shouldBe
        A.PublicationRoute.MissingAuthority
      A.publicationRoute(F.narrowFailure, A.scope(F.narrowFailure).copy(targets = Vector(F.target)), Vector(F.narrowBroad),
        next, "51", validation.generation) shouldBe A.PublicationRoute.MissingAuthority
      F.narrowFailure.primaryDirections shouldBe F.narrowFact.failure.primaryDirections
    }
  }

  "Primary repair source attribution" should {
    "preserve old dirty unselected paths while permitting new declared-owned corrective files" in {
      Given("the failure's immutable unselected baseline and a newly edited owned corrective source")
      val dirty = Vector(G.DirtyPath('M', 'M', "src/primary.scala", None),
        G.DirtyPath('?', '?', "src/extra.scala", None)) ++ F.excluded
      When("ordinary candidate selection excludes the original unrelated delta")
      val selected = take(A.repairSelectionC(F.plan.steps.head.ownedPaths, F.plan.steps.head.ownedPaths, dirty, F.excluded))
      Then("both actual corrective sources are eligible and the sentinel remains unselected")
      selected shouldBe Vector("src/primary.scala", "src/extra.scala")
      selected should not contain "docs/preserved.md"
    }

    "refine broad registered directories to corrective children while preserving excluded siblings" in {
      Given("unchanged broad host and Step paths with original unrelated docs and newly dirty corrective files")
      val registered = Vector("docs", "src")
      val excluded = Vector(G.DirtyPath('?', '?', "docs/preserved.md", None))
      val dirty = Vector(G.DirtyPath('M', 'M', "src/primary.scala", None),
        G.DirtyPath('?', '?', "docs/primary-repair-note.md", None)) ++ excluded
      When("the repair derives concrete staging paths from the actual typed records")
      val selected = take(A.repairSelectionC(registered, registered, dirty, excluded))
      Then("registered selector order determines the exact subset without selecting a directory or excluded child")
      selected shouldBe Vector("docs/primary-repair-note.md", "src/primary.scala")
      selected should not contain "docs/preserved.md"
      selected.intersect(registered) shouldBe empty
    }

    "select both safe rename endpoints within host and Step coverage" in {
      Given("a corrective rename whose source and destination are both owned and host-covered")
      val renamed = G.DirtyPath('R', ' ', "src/corrected.scala", Some("src/original.scala"))
      When("the repair attributes the rename's concrete staging paths")
      val selected = take(A.repairSelectionC(Vector("src"), Vector("src"), Vector(renamed), Vector.empty))
      Then("both endpoints are selected once in typed record order")
      selected shouldBe Vector("src/corrected.scala", "src/original.scala")
    }

    "refuse a rename crossing either Step ownership or host coverage" in {
      Given("renames with only one endpoint covered by the Step or the host")
      val cases = Vector(
        (Vector("src", "outside"), Vector("src"), G.DirtyPath('R', ' ', "src/new.scala", Some("outside/old.scala"))),
        (Vector("src", "outside"), Vector("src"), G.DirtyPath('R', ' ', "outside/new.scala", Some("src/old.scala"))),
        (Vector("src"), Vector("src", "outside"), G.DirtyPath('R', ' ', "src/new.scala", Some("outside/old.scala"))),
        (Vector("src"), Vector("src", "outside"), G.DirtyPath('R', ' ', "outside/new.scala", Some("src/old.scala"))))
      When("the repair requires both endpoints to satisfy both boundaries")
      val accepted = cases.map { case (registered, owned, renamed) =>
        A.repairSelectionC(registered, owned, Vector(renamed), Vector.empty).isSuccess
      }
      Then("no crossing rename is partially staged")
      accepted shouldBe Vector.fill(cases.size)(false)
    }

    "refuse renames touching an originally excluded source or destination" in {
      Given("an immutable excluded file and corrective renames crossing that file in either direction")
      val excluded = Vector(G.DirtyPath('?', '?', "docs/preserved.md", None))
      val renames = Vector(
        G.DirtyPath('R', ' ', "docs/correction.md", Some("docs/preserved.md")),
        G.DirtyPath('R', ' ', "docs/preserved.md", Some("docs/correction.md")))
      When("the repair authenticates the excluded baseline and rename endpoints")
      val accepted = renames.map(renamed =>
        A.repairSelectionC(Vector("docs"), Vector("docs"), excluded :+ renamed, excluded).isSuccess)
      Then("neither endpoint can transfer an unrelated baseline delta into the candidate")
      accepted shouldBe Vector(false, false)
    }

    "keep new outside dirt unselected and refuse path-prefix lookalikes as coverage" in {
      Given("a corrective docs child plus an unrelated new path with a similar directory prefix")
      val correction = G.DirtyPath('?', '?', "docs/correction.md", None)
      val outside = G.DirtyPath('?', '?', "docs-other/unrelated.md", None)
      When("ownership and host coverage are interpreted at slash boundaries")
      val selected = take(A.repairSelectionC(Vector("docs"), Vector("docs"), Vector(outside, correction), Vector.empty))
      val unhosted = A.repairSelectionC(Vector("docs"), Vector("docs-other"), Vector(outside), Vector.empty)
      val unowned = A.repairPathsValid(Vector("docs-other"), Vector("docs"), Vector(outside.path), Vector.empty)
      Then("outside dirt is ignored while lookalikes cannot satisfy either admission boundary")
      selected shouldBe Vector(correction.path)
      unhosted.isSuccess shouldBe false
      unowned shouldBe false
    }

    "deduplicate concrete children covered by overlapping registered selectors" in {
      Given("overlapping broad and exact registered selectors with two actual corrective records")
      val registered = Vector("src", "src/main", "src/main/primary.scala")
      val dirty = Vector(G.DirtyPath('M', 'M', "src/main/primary.scala", None),
        G.DirtyPath('?', '?', "src/main/extra.scala", None))
      When("selection follows registered order across all covering selectors")
      val selected = take(A.repairSelectionC(registered, Vector("src"), dirty, Vector.empty))
      Then("each actual path appears once even when multiple host selectors cover it")
      selected shouldBe Vector("src/main/primary.scala", "src/main/extra.scala")
      selected.distinct shouldBe selected
    }

    "refuse duplicate unmerged malformed and ambiguous actual dirty records" in {
      Given("invalid typed observations that cannot identify one safe corrective delta")
      val correction = G.DirtyPath('M', 'M', "src/primary.scala", None)
      val cases = Vector(
        Vector(correction, correction),
        Vector(G.DirtyPath('U', 'U', correction.path, None)),
        Vector(correction.copy(path = "src/../foreign.scala")),
        Vector(correction.copy(index = 'R')),
        Vector(correction.copy(renamedFrom = Some(correction.path))),
        Vector(correction.copy(renamedFrom = null)),
        Vector(correction.copy(index = 'X')),
        Vector(correction, G.DirtyPath('R', ' ', "src/renamed.scala", Some(correction.path))))
      When("the repair validates the complete dirty observation before selecting concrete paths")
      val accepted = cases.map(dirty => A.repairSelectionC(Vector("src"), Vector("src"), dirty, Vector.empty).isSuccess)
      Then("invalid or multiply attributed endpoints refuse instead of becoming staging data")
      accepted shouldBe Vector.fill(cases.size)(false)
    }

    "preserve excluded status through the native scoped dirty guard before and after staging" in {
      Given("an original untracked excluded child beside an owned corrective path")
      val excluded = Vector(G.DirtyPath('?', '?', "docs/preserved.md", None))
      val before = excluded :+ G.DirtyPath('M', 'M', "docs/correction.md", None)
      val after = excluded :+ G.DirtyPath('M', ' ', "docs/correction.md", None)
      val mutations = Vector(
        after.updated(0, excluded.head.copy(index = 'A', worktree = ' ')),
        after.updated(0, excluded.head.copy(index = ' ', worktree = 'M')),
        after.tail,
        after :+ G.DirtyPath('R', ' ', "docs/renamed.md", Some(excluded.head.path)))
      When("native candidate inspection checks broad ownership while filtering only exact unchanged excluded records")
      val permitted = Vector(C.primaryRepairDirtyValid(before, Vector("docs"), excluded, false),
        C.primaryRepairDirtyValid(after, Vector("docs"), excluded, true))
      val changed = mutations.map(dirty => C.primaryRepairDirtyValid(dirty, Vector("docs"), excluded, true))
      Then("ordinary repair staging is permitted and every excluded status or rename mutation refuses")
      permitted shouldBe Vector(true, true)
      changed shouldBe Vector.fill(mutations.size)(false)
    }

    "refuse excluded rename endpoints and ancestor ambiguity in retained paths and the global index" in {
      Given("an originally unselected rename whose two endpoints remain protected under broad docs ownership")
      val excluded = Vector(G.DirtyPath('R', ' ', "docs/preserved.md", Some("docs/original.md")))
      val registered = Vector("docs", "src")
      val invalid = Vector(Vector("docs/preserved.md"), Vector("docs/original.md"), Vector("docs"),
        Vector("docs/preserved.md/child"), Vector("src", "src/primary.scala"),
        Vector("src/primary.scala", "src/primary.scala"), Vector("src-other/primary.scala"))
      When("the shared retained-path and pending-index predicate checks concrete repair attribution")
      val safe = A.repairPathsValid(registered, registered, Vector("docs/correction.md", "src/primary.scala"), excluded)
      val accepted = invalid.map(paths => A.repairPathsValid(registered, registered, paths, excluded))
      Then("only distinct covered paths disjoint from both excluded endpoints are admitted")
      safe shouldBe true
      accepted shouldBe Vector.fill(invalid.size)(false)
    }

    "confirm an already indexed deletion only from its complete current staged status" in {
      Given("a concrete selected owned path with a current staged deletion and unchanged excluded sibling")
      val path = "docs/removed.md"
      val excluded = Vector(G.DirtyPath('?', '?', "docs/preserved.md", None))
      val dirty = excluded :+ G.DirtyPath('D', ' ', path, None)
      When("the authenticated repair interprets the pinned native status for an already removed path")
      val broad = A.stagedRemovalObserved(Vector("docs"), Vector("docs"), Vector(path), dirty, excluded, path)
      val exact = A.stagedRemovalObserved(Vector(path), Vector(path), Vector(path), dirty, excluded, path)
      Then("both broad and exact registration can confirm indexed removal without another add effect")
      broad shouldBe true
      exact shouldBe true
    }

    "confirm only the old endpoint of a staged rename with both literal endpoints selected" in {
      Given("a staged rename whose old endpoint has no standalone current status record")
      val oldpath = "docs/phase.md"
      val newpath = "docs/primary-repair-phase.md"
      val renamed = G.DirtyPath('R', ' ', newpath, Some(oldpath))
      val selected = Vector(newpath, oldpath)
      When("the repair distinguishes observed source removal from destination staging")
      val oldconfirmed = A.stagedRemovalObserved(Vector("docs"), Vector("docs"), selected,
        Vector(renamed), Vector.empty, oldpath)
      val modifieddestination = A.stagedRemovalObserved(Vector("docs"), Vector("docs"), selected,
        Vector(renamed.copy(worktree = 'M')), Vector.empty, oldpath)
      val destinationconfirmed = A.stagedRemovalObserved(Vector("docs"), Vector("docs"), selected,
        Vector(renamed), Vector.empty, newpath)
      Then("the indexed source removal is confirmed while the destination still requires normal typed staging")
      oldconfirmed shouldBe true
      modifieddestination shouldBe true
      destinationconfirmed shouldBe false
    }

    "refuse to confirm unstaged reintroduced untracked malformed or ambiguous removals" in {
      Given("selected removal paths with statuses that do not prove one absent indexed endpoint")
      val path = "docs/phase.md"
      val newpath = "docs/primary-repair-phase.md"
      val deleted = G.DirtyPath('D', ' ', path, None)
      val renamed = G.DirtyPath('R', ' ', newpath, Some(path))
      val cases = Vector(
        Vector(deleted.copy(index = ' ', worktree = 'D')),
        Vector(deleted.copy(worktree = 'M')),
        Vector(G.DirtyPath('?', '?', path, None)),
        Vector(deleted, G.DirtyPath('?', '?', path, None)),
        Vector(renamed, G.DirtyPath('?', '?', path, None)),
        Vector(renamed, G.DirtyPath('M', ' ', path, None)),
        Vector(renamed.copy(index = ' ', worktree = 'R')),
        Vector(renamed.copy(index = 'C')),
        Vector(renamed.copy(renamedFrom = null)),
        Vector(renamed, renamed.copy(path = "docs/another.md")),
        Vector(deleted.copy(index = 'U', worktree = 'U')),
        Vector(G.DirtyPath('M', ' ', path, None)))
      When("the repair evaluates whether native status alone can confirm the selected removal")
      val confirmed = cases.map(dirty => A.stagedRemovalObserved(Vector("docs"), Vector("docs"),
        Vector(path, newpath), dirty, Vector.empty, path))
      Then("every nonremoval or uncertain record keeps the ordinary typed staging path")
      confirmed shouldBe Vector.fill(cases.size)(false)
    }

    "refuse removal confirmation outside selected ownership host coverage or the excluded baseline" in {
      Given("a real staged rename whose confirmation still requires both exact selected endpoints and protected ownership")
      val oldpath = "docs/phase.md"
      val newpath = "docs/primary-repair-phase.md"
      val renamed = G.DirtyPath('R', ' ', newpath, Some(oldpath))
      val selected = Vector(newpath, oldpath)
      val excluded = Vector(G.DirtyPath('?', '?', oldpath, None))
      When("the repair checks selection coverage and immutable excluded records before confirming native removal")
      val confirmed = Vector(
        A.stagedRemovalObserved(Vector("docs"), Vector("docs"), Vector(oldpath), Vector(renamed), Vector.empty, oldpath),
        A.stagedRemovalObserved(Vector("docs"), Vector("docs"), Vector(newpath), Vector(renamed), Vector.empty, oldpath),
        A.stagedRemovalObserved(Vector("docs"), Vector("src"), selected, Vector(renamed), Vector.empty, oldpath),
        A.stagedRemovalObserved(Vector("src"), Vector("docs"), selected, Vector(renamed), Vector.empty, oldpath),
        A.stagedRemovalObserved(Vector("docs"), Vector("docs"), selected, Vector(renamed), excluded, oldpath),
        A.stagedRemovalObserved(Vector("docs"), Vector("docs"), selected, Vector(renamed) ++ excluded, excluded, oldpath))
      Then("native observation cannot supply missing selection permission or absorb an excluded file")
      confirmed shouldBe Vector.fill(confirmed.size)(false)
    }

    "bind each refined child to its existing covering goal-path identity and preserve exact registrations" in {
      Given("fixed directory programs plus a legacy exact registered source path")
      val registered = Vector("docs", "src", "src/primary.scala")
      When("the repair constructs single literal typed selections without changing the registered operation")
      val child = take(C.primaryRepairSelectionC(registered, "docs/correction.md"))
      val exact = take(C.primaryRepairSelectionC(registered, "src/primary.scala"))
      val sourcechild = take(C.primaryRepairSelectionC(registered, "src/extra.scala"))
      val refused = Vector("docs-other/file.md", "docs/../foreign.md", "/docs/file.md").map(path =>
        C.primaryRepairSelectionC(registered, path).isSuccess)
      Then("concrete children retain the covering ordinal while exact paths retain their original ordinal")
      child.identity shouldBe take(GitSelectionId.parseC("goal-path-1"))
      child.paths.map(_.value) shouldBe Vector("docs/correction.md")
      child.message shouldBe None
      exact.identity shouldBe take(GitSelectionId.parseC("goal-path-3"))
      exact.paths.map(_.value) shouldBe Vector("src/primary.scala")
      sourcechild.identity shouldBe take(GitSelectionId.parseC("goal-path-2"))
      sourcechild.paths.map(_.value) shouldBe Vector("src/extra.scala")
      refused shouldBe Vector(false, false, false)
    }
  }
}
