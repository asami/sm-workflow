package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Printer}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
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
  val generationDiagnostic = "[error] [{\"code\":\"GENERATION_PROVENANCE_OUTPUT_TAMPERED\"," +
    "\"source\":\"/specification/target/sbt-cozy/delegate-work/run-0\"," +
    "\"expected\":\"at least one generated Scala artifact\",\"actual\":\"none\"," +
    "\"message\":\"Generation provenance cannot describe an empty output.\"," +
    "\"correctiveAction\":\"Complete Scala source generation before writing provenance.\"}]\n"
  val generationSummary = "[error] (cozyGenerate) [sbt-cozy] cozy delegate failed (1) for /specification/src/main/cozy/product.cml\n"
  val generationOutput = "[error] Error[No input]\n" + generationDiagnostic + generationSummary +
    "[error] Error[No input]\n" + generationDiagnostic
  val structuralMarker = "[error] Error[org.goldenport.exception.SyntaxErrorFaultException: WORKFLOW 'RepositorySyncWorkflow' " +
    "OPERATION 'refreshRepositoryDirectoryConfiguration' does not admit nested structural content.]"
  val requiredStructuralMarker = "[error] Error[org.goldenport.exception.SyntaxErrorFaultException: WORKFLOW '注文-workflow.v1' " +
    "REQUIRED-OPERATION 'capture-payment' does not admit nested structural content.]"
  def structuralOutput(marker: String = structuralMarker): String = {
    val metadata = "[error] [sbt-cozy] generator: org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT\n" +
      "[error] [sbt-cozy] command: cozy --runtime 0.3.3-SNAPSHOT sbt-bridge v1 --request /specification/request.json\n" +
      "[error] [sbt-cozy] cwd: /specification\n" +
      "[error] [sbt-cozy] recovery: publish-or-select-the-exact-project-owned-Cozy-generator-coordinate\n" +
      "[error] [sbt-cozy] recent logs:\n"
    val notice = "[error] [cozy.generation.acceptance] boundary=sbt-bridge level=notice code=MutableDevelopmentPairAccepted " +
      "lifecycle=development source=cli pair=org.goldenport:goldenport-cncf_3:0.5.6-SNAPSHOT|org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT " +
      "evidence=META-INF/cozy/generation-compatibility.v1.json message=Explicit mutable development generation pair accepted: " +
      "org.goldenport:goldenport-cncf_3:0.5.6-SNAPSHOT|org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT.\n"
    "[warn] [sbt-cozy/cozy] " + generationDiagnostic.stripPrefix("[error] ") +
      "[error] java.lang.RuntimeException: [sbt-cozy] cozy delegate failed (1) for /specification/src/main/cozy/product.cml\n" +
      metadata + marker + "\n" + notice + generationDiagnostic +
      "[error] \tat scala.sys.package$.error(package.scala:30)\n" +
      "[error] \tat java.base/java.lang.Thread.run(Thread.java:1474)\n" + generationSummary +
      metadata + marker + "\n" + notice + generationDiagnostic + "[error] Total time: 13 s, completed 2026/11/08 19:36:14\n"
  }
  def streamProcess(stdout: String, stderr: String): Json = {
    def _capture_(text: String): ProcessExecutionCapture = {
      val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      ProcessExecutionCapture(bytes, bytes.size.toLong, false)
    }
    WorkflowSbtCapture.encode(ProcessExecutionResult(ProcessExecutionTermination.Exited(1), _capture_(stdout), _capture_(stderr),
      Vector.empty, 66358L, program))
  }
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
  val generationFailure = failure.copy(failed = failure.failed.copy(reason = "Specification terminated generation failure",
    process = Some(process(generationOutput))))
  val generationRepair = take(A.stateC(controller, generationFailure))
  val generationFact = A.Fact(A.generationSchema, generationFailure, A.directive(generationFailure), sorted(B.controller(generationRepair)))
  def originalTarget(failure: A.Failure): A.Target = A.Target(A.publicationSchema(failure), failure.componentInstance,
    failure.participant, failure.sessionId, failure.frontierRevision, failure.controller, failure.generation,
    failure.candidateRecordRevision, failure.candidate, failure, None, A.scope(failure), "primary-source-repair")
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
  def sourceCheckFailure(capture: GoalPhaseDependencySourceRevision.Capture): W.CheckFailureContext = {
    val validation = primaryValidation(false)
    val receipt = validation.receipts.head
    val consumption = GoalPhaseDependencySourceRevision.consumptionOf(capture)
    val program = consumption.programs.find(_.check == receipt.identity).get.program
    val observed = WorkflowSbtCapture.decode(process(text = receipt.stdout, identity = program))
      .fold(error => throw new IllegalArgumentException("Invalid specification process: " + error), identity)
    val binding = GoalPhaseDependencySourceRevision.bindingOf(capture)
    take(W.checkFailureContextC(Vector("screen.scala"), "SPECIFICATION-OWNED retained failed primary diff",
      validation.copy(selected = validation.selected ++ binding.checks.map(_.copy(dependency = Some(binding.identity))),
        receipts = Vector(receipt.copy(processObservation = Some(observed))), dependencySource = Some(capture),
        sourceConsumption = Some(consumption))))
  }
  def nativeRecord(revision: Long): InstanceRecord = {
    val initial = take(InstanceRecord(InstanceIdentity("same-session"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("primary-successor-specification"),
      CausationReference("primary-successor-start"), None, None, None, Vector.empty).validateC)
    (initial.revision.value until revision).foldLeft(initial)((previous, index) =>
      take(previous.appendC(previous.revision, GoalPhaseNativeRun.history(previous, "phase.InspectCandidate",
        "specification-successor-" + index))))
  }
  def sourceSuccessor(): (GoalPhaseDependencySourceRevision.Direction, GoalPhaseDependencySourceRevision.ScopeSuccessor,
    GoalPhaseDependencySourceRevision.Capture, C.Payload) = {
    import GoalPhaseDependencySourceRevision as R
    val original = P.sourceDirection
    val path = "docs/current-integration.md"
    val approval = D.approvals.head.copy(proposal = D.approvals.head.proposal.copy(
      identity = "current-integration-proposal", discovery = "REPAIR", paths = Vector(path),
      acceptance = Vector("Qualify the committed integration document")), decision = ContextReference("current-scope-human", "1"))
    val binding = original.target.binding.copy(revision = 3, paths = original.target.binding.paths :+ path,
      base = O.Snapshot("6" * 40, "7" * 40))
    val consumption = P.consumption.copy(expectedModes = P.consumption.expectedModes :+ R.ExpectedMode(path, "100644"))
    val coverage = original.target.proposal.coverage :+ R.Coverage(approval.decision, approval.proposal.acceptance,
      binding.checks.map(_.identity))
    val guard = P.postGuard.copy(head = binding.base.head, committedTree = binding.base.tree, indexTree = binding.base.tree,
      dirty = Vector.empty, selectedFiles = P.postGuard.selectedFiles :+
        R.fileIdentity(path, Some("SPECIFICATION-OWNED current integration\n".getBytes.toVector)),
      selectedEntries = binding.paths.map(p => R.Entry(p, "100644", "8" * 40)))
    val changes = Vector(R.StageChange("100644", "100644", Some("1" * 40), Some("8" * 40), "M", binding.paths.head),
      R.StageChange("000000", "100644", None, Some("8" * 40), "A", path))
    val committed = R.CommittedBaseSuccession(R.committedBaseSuccessionSchema, original.directive,
      original.target.binding.base, binding.base, changes, changes.map(_.path), Vector.empty, Vector.empty,
      Vector.empty, "committed", binding.paths.map(p => R.CommittedEntry(p, Some(R.CommittedObject("100644", "8" * 40)))))
    val target = original.target.copy(schemaVersion = R.sourceSchema, questionIdentity = "current-source-question",
      sourceRevision = "50", pendingRevision = "51", generation = 2, binding = binding, preStageGuard = guard,
      proposal = original.target.proposal.copy(binding = ContextReference(binding.identity, "3"), mode = "committed",
        consumption = consumption, coverage = coverage), primaryFailureOrigin = None,
      sourceSuccession = Some(R.SourceSuccession(original.directive, Vector(approval), original.directive)),
      committedBaseSuccession = Some(committed))
    val next = R.Direction(target, ContextReference("current-source-human", "1"), "participant", "SPECIFICATION-OWNED current source")
    val scope = R.ScopeSuccessor(R.sourceScopeSchema, original.directive, binding, Vector(approval), consumption, coverage, Some(next))
    val capture = P.capture.copy(sourceRevision = "60", generation = 3, stagedPaths = Vector.empty,
      preStageGuard = guard, postStageGuard = guard, scope = Some(scope))
    val candidate = narrowCandidate.copy(sourceRevision = "60", dependencySourceScope = Some(scope),
      dependencySourceGuard = Some(guard), dependencySourceStages = Vector.empty, dependencySourceCapture = Some(capture))
    (next, scope, capture, candidate)
  }
  def policySourceSuccessor(): (C.Payload, C.Payload) = {
    import GoalPhaseDependencySourceRevision as R
    import DependencySourceRevisionSpecificationFixture as S
    val path = "docs/approved-current.md"
    val approval = D.approvals.head.copy(proposal = D.approvals.head.proposal.copy(identity = "policy-current-scope",
      discovery = "REPAIR", paths = Vector(path), acceptance = Vector("Qualify the approved current document")),
      decision = ContextReference("policy-scope-human", "1"))
    val binding = S.binding.copy(revision = S.binding.revision + 1, paths = S.binding.paths :+ path,
      base = O.Snapshot("6" * 40, "7" * 40))
    val consumption = R.Consumption(S.proposal.consumption.primaryWorkspace, S.binding.root, S.proposal.excludedPaths,
      S.proposal.consumption.expectedModes :+ R.ExpectedMode(path, "100644"), S.proposal.consumption.programs)
    val coverage = S.proposal.coverage :+ R.Coverage(approval.decision, approval.proposal.acceptance, binding.checks.map(_.identity))
    val guard = S.before.copy(head = binding.base.head, committedTree = binding.base.tree, indexTree = binding.base.tree,
      selectedFiles = S.before.selectedFiles :+ R.fileIdentity(path, Some("Approved current document\n".getBytes.toVector)),
      selectedEntries = binding.paths.map(p => R.Entry(p, "100644", "8" * 40)))
    val changes = Vector(R.StageChange("000000", "100644", None, Some("8" * 40), "A", path))
    val committed = R.CommittedBaseSuccession(R.committedBaseSuccessionSchema, S.direction.directive, S.binding.base,
      binding.base, changes, Vector(path), Vector.empty, Vector.empty, Vector.empty, "committed",
      binding.paths.map(p => R.CommittedEntry(p, Some(R.CommittedObject("100644", "8" * 40)))))
    val target = S.target.copy(schemaVersion = R.sourceSchema, questionIdentity = "policy-source-question", sourceRevision = "50",
      pendingRevision = "51", generation = 2, binding = binding, preStageGuard = guard,
      proposal = S.proposal.copy(schemaVersion = R.sourceSchema, binding = ContextReference(binding.identity, binding.revision.toString),
        consumption = consumption, coverage = coverage),
      sourceSuccession = Some(R.SourceSuccession(S.direction.directive, Vector(approval), S.direction.directive)),
      committedBaseSuccession = Some(committed))
    val next = R.Direction(target, ContextReference("policy-source-human", "1"), "participant", "SPECIFICATION-OWNED source succession")
    val scope = R.ScopeSuccessor(R.sourceScopeSchema, S.direction.directive, binding, Vector(approval), consumption, coverage, Some(next))
    val capture = S.capture.copy(schemaVersion = R.sourceCaptureSchema, sourceRevision = "60", generation = 3,
      preStageGuard = guard, postStageGuard = guard, scope = Some(scope))
    val previous = C.Payload(S.capture.sourceRevision, S.capture.step, Vector.empty, Vector.empty, None, Vector.empty, None,
      dependencySourceDirection = Some(S.direction), dependencySourceGuard = Some(S.before), dependencySourceCapture = Some(S.capture))
    (previous, previous.copy(sourceRevision = "60", dependencySourceScope = Some(scope),
      dependencySourceGuard = Some(guard), dependencySourceCapture = Some(capture)))
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
  *  version Nov.  9, 2026
  * @version Nov. 10, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePrerequisitePrimaryRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PrimaryPublicationRepairSpecificationFixture as F
  "Committed primary publication target" should {
    "use complete qualified work without rewriting the original nonempty failure or granting publication" in {
      Given("P611-CHECKPOINT-A-01: the original literal publication failure and a later clean repaired checkpoint")
      val checkpoint = O.Snapshot("e" * 40, "f" * 40)
      val proof = O.CandidateContinuation(checkpoint, Vector(
        O.HistoryCommit(checkpoint.head, checkpoint.tree, Vector(F.base.head)),
        O.HistoryCommit(F.base.head, F.base.tree, Vector.empty)))
      val raw = F.repairedCandidate.copy(candidate = Some(checkpoint), continuation = Some(proof),
        changedPaths = Vector.empty, diff = Some(""))
      val work = GoalPhaseCommittedStepWork.Work(ContextReference("primary-session", "17"), F.base, proof,
        F.candidate.changedPaths, F.candidate.diff.get)
      val qualified = raw.copy(committedWork = Some(work))
      val inspecting = take(F.repair.acceptC(O.SemanticCompleted(F.repair.next.asInstanceOf[O.Semantic].key,
        "Specification corrected work already committed")))
      val current = take(inspecting.acceptC(O.CandidateContinuationObserved(checkpoint, proof)))
      val target = A.Target(A.targetSchema, F.failure.componentInstance, F.failure.participant, F.failure.sessionId,
        "18", F.sorted(B.controller(current)), current.generation, "18", qualified, F.failure,
        Some(F.reuse.repair), A.scope(F.failure), "existing-primary-publication-authority")
      When("the current Target is validated using its qualified complete work")
      val valid = A.targetValid(target)
      Then("the Target remains eligible while the original failure keeps its literal nonempty candidate")
      valid shouldBe true
      A.targetValid(target.copy(candidate = raw)) shouldBe false
      A.targetValid(target.copy(candidate = qualified.copy(committedWork = Some(work.copy(paths = Vector("foreign.scala")))))) shouldBe false
      A.targetValid(target.copy(scope = target.scope.copy(processPolicy = "{\"crossed\":true}"))) shouldBe false
      A.failureValid(F.failure) shouldBe true
      A.failureValid(F.failure.copy(candidate = qualified)) shouldBe false
      target.failure.candidate.changedPaths shouldBe F.candidate.changedPaths
      target.failure.candidate.diff shouldBe F.candidate.diff
      C.workPaths(target.candidate) shouldBe F.candidate.changedPaths
      target.candidate.changedPaths shouldBe empty
    }
  }
  private def _inspection_source_fixture: (O.State, GoalPhaseDependencySourceRevision.Direction,
    GoalPhaseDependencySourceRevision.Capture, C.Payload) =
    _inspection_source_fixture_for(subsequentrepair = false, applicability = false)

  private def _inspection_source_fixture_for(subsequentrepair: Boolean, applicability: Boolean):
    (O.State, GoalPhaseDependencySourceRevision.Direction,
    GoalPhaseDependencySourceRevision.Capture, C.Payload) = {
    import LaterSourcePublicationSpecificationFixture as L
    val completed = take(L.successor.acceptC(O.SemanticCompleted(L.successor.next.asInstanceOf[O.Semantic].key,
      "SPECIFICATION-ONLY accepted current Repair before checkpoint")))
    val accepted = if (!subsequentrepair) completed else {
      val inspected = take(completed.acceptC(O.CandidateObserved(O.Snapshot(completed.tipSnapshot.head, "8" * 40))))
      val failed = take(inspected.acceptC(O.RepairableChecksObserved(inspected.candidate.get,
        Vector("primary-later"), "specification-subsequent-failed-check",
        O.RepairDirective("SPECIFICATION-ONLY subsequent ordinary repair", Vector("Correct the newly failed check")))))
      take(failed.acceptC(O.SemanticCompleted(failed.next.asInstanceOf[O.Semantic].key,
        "SPECIFICATION-ONLY accepted subsequent ordinary Repair before checkpoint")))
    }
    _inspection_source_from(accepted, L.saved.reuse, applicability)
  }

  private def _inspection_source_from(accepted: O.State, reuse: A.CaptureReuse, applicability: Boolean,
    prior: GoalPhaseDependencySourceRevision.Direction = LaterSourcePublicationSpecificationFixture.direction):
    (O.State, GoalPhaseDependencySourceRevision.Direction, GoalPhaseDependencySourceRevision.Capture, C.Payload) = {
    import GoalPhaseDependencySourceRevision as R
    import GoalPhaseCanonicalDependencySourceSuccession as Source
    import GoalPhaseDevelopmentReconstruction as X
    import GoalPhasePlanRevision as PlanRevision
    import LaterSourcePublicationSpecificationFixture as L
    val selected = take(accepted.reconstructDevelopmentC(accepted.plan, changed = true, sourceTargetGeneration = None))
    val raw = PlanRevision.Proposal("same-purpose-checkpoint", "1", "Preserve accepted Repair and select current source",
      selected.plan.copy(effectiveAcceptance = None))
    val requesting = ContextReference("accepted-checkpoint-repair", "1")
    val payload = X.Payload("same-session", "89", L.origin.participant, L.origin.componentInstance,
      ContextReference("specification-definition", "1"),
      X.Documents("docs/phase.md", "docs/checklist.md", "Same remaining purpose", "Same required acceptance",
        PlanRevision.Captured(raw, selected.plan)),
      X.Observation(L.origin.primaryRoot, "main", "a" * 40, "b" * 40, "b" * 40, Vector.empty),
      X.Assessment(Vector.empty, Vector.empty, Vector.empty, freshCapture = true, retainEvidence = false),
      "SPECIFICATION-ONLY native-shaped checkpoint proof; actual history supplies authority", None,
      Some(requesting), None, None, selected.plan.phaseOwnedPaths)
    val frontier = Source.InspectionFrontier(ContextReference("same-session", "90"), payload)
    val binding = prior.target.binding.copy(revision = prior.target.binding.revision + 1, base = O.Snapshot("6" * 40, "7" * 40))
    val publication = if (applicability) {
      val fact = CommittedNoninputApplicabilitySpecificationFixture.fact(L.publication.result.publication,
        LocalPublication.Source(binding.base.head, binding.base.tree, Vector.empty), binding.root, binding.branch,
        L.publication.destination, L.publication.artifact)
      L.publication.copy(result = L.publication.result.copy(applicability = Some(fact)))
    } else L.publication.copy(result = L.publication.result.copy(publication =
      L.publication.result.publication.copy(request = L.publication.result.publication.request.copy(
        source = LocalPublication.Source(binding.base.head, binding.base.tree, Vector.empty)))))
    val proposal = L.proposal.copy(schemaVersion = if (applicability) Source.applicabilityProposalSchema else Source.proposalSchema,
      source = L.proposal.source.copy(binding = ContextReference(binding.identity, binding.revision.toString)),
      publication = publication)
    val guard = L.guard.copy(head = binding.base.head, committedTree = binding.base.tree, indexTree = binding.base.tree)
    val previouspolicy = io.circe.parser.parse(prior.target.canonicalOrigin.get.processPolicy).toOption.get
    val processplan = previouspolicy.hcursor.get[Json]("processPlan").toOption.get
    val processpolicy = F.sorted(previouspolicy.mapObject(_.add("processPlan", processplan.mapObject(_.add("gitPrograms",
      Vector(Json.obj("identity" -> "specification-current-committed-inventory".asJson,
        "target" -> binding.dependency.workspace.asJson, "operation" -> GitOperationKind.TreeEntries.wireName.asJson,
        "selectionId" -> R.committedInventorySelection.asJson, "selectedPaths" -> binding.paths.asJson)).asJson)))))
    val origin = L.origin.copy(controller = GoalPhaseCandidateRecovery.controllerSnapshot(selected),
      originalDirection = Some(prior), previousDirection = None, selection = proposal, processPolicy = processpolicy,
      baseLineage = Vector(O.HistoryCommit(binding.base.head, binding.base.tree, Vector(prior.target.binding.base.head)),
        O.HistoryCommit(prior.target.binding.base.head, prior.target.binding.base.tree, Vector.empty)),
      historicalPublicationFailure = None, acceptedSourcePrefix = None, inspectionFrontier = Some(frontier))
    val target = L.target.copy(schemaVersion = if (applicability) R.canonicalApplicabilitySchema else R.canonicalSchema,
      sourceRevision = "90", pendingRevision = "90",
      generation = selected.generation, repairProgress = GoalPhaseRepairProgress.snapshot(selected),
      requestingResult = requesting, proposal = proposal.source, binding = binding, preStageGuard = guard,
      canonicalOrigin = Some(origin))
    val direction = L.direction.copy(target = target, directive = ContextReference("inspection-source-human", "1"))
    val controller = take(selected.adoptCanonicalDependencySourceC(direction.directive))
    val capture = L.capture.copy(schemaVersion = if (applicability) R.canonicalApplicabilityCaptureSchema else R.canonicalCaptureSchema,
      generation = controller.generation,
      sourceRevision = "94", direction = direction, preStageGuard = guard, postStageGuard = guard)
    val candidate = L.candidate.copy(sourceRevision = "94", dependencySourceDirection = Some(direction),
      dependencySourceGuard = Some(guard), dependencySourceCapture = Some(capture), primaryPublicationRepair = Some(reuse))
    (controller, direction, capture, candidate)
  }

  /** Specification-owned records accompany real pure controller transitions;
    * these values do not authenticate a loaded native Session or grant effects. */
  private def _second_primary_failure_fixture: (A.Saved, O.State, GoalPhaseDependencySourceRevision.Direction,
    GoalPhaseDependencySourceRevision.Capture, C.Payload) = {
    import LaterSourcePublicationSpecificationFixture as L
    val policy = io.circe.parser.parse(L.origin.processPolicy).toOption.get
    val process = policy.hcursor.get[Json]("processPlan").toOption.get
    val inventory = Json.obj(
      "identity" -> "specification-prior-committed-inventory".asJson, "target" -> L.binding.dependency.workspace.asJson,
      "operation" -> GitOperationKind.TreeEntries.wireName.asJson,
      "selectionId" -> GoalPhaseDependencySourceRevision.committedInventorySelection.asJson,
      "selectedPaths" -> L.binding.paths.asJson)
    val priorpolicy = F.sorted(policy.mapObject(_.add("processPlan", process.mapObject(_.add("gitPrograms", Json.arr(inventory))))))
    val prior = L.direction.copy(target = L.target.copy(canonicalOrigin = Some(L.origin.copy(processPolicy = priorpolicy))))
    val selected = take(L.repair.adoptCanonicalDependencySourceC(prior.directive))
    val accepted = take(selected.acceptC(O.SemanticCompleted(selected.next.asInstanceOf[O.Semantic].key,
      "SPECIFICATION-ONLY accepted first primary Repair")))
    val validation = take(accepted.acceptC(O.CandidateObserved(O.Snapshot(accepted.tipSnapshot.head, "8" * 40))))
    val candidate = L.candidate.copy(candidate = validation.candidate, diff = Some("SPECIFICATION-ONLY second primary Compile failure"),
      dependencySourceDirection = Some(prior), dependencySourceCapture = Some(L.capture.copy(direction = prior)))
    val input = LocalPublicationRuntime.prerequisiteInput("same-session", candidate, L.primary)
    val publication = L.failed.copy(request = L.failed.request.copy(
      input = input.copy(requestIdentity = input.requestIdentity + ":prerequisite:0"),
      source = LocalPublication.Source(validation.candidate.get.head, validation.candidate.get.tree, Vector.empty)))
    val failure = L.failure.copy(frontierRevision = "78", controller = F.sorted(B.controller(validation)),
      generation = validation.generation, repairProgress = GoalPhaseRepairProgress.snapshot(validation),
      candidateRecordRevision = "76", candidate = candidate, requestingResult = ContextReference("second-primary-implementation", "1"),
      processPolicy = priorpolicy, failed = publication)
    val repair = take(A.stateC(validation, failure))
    val fact = A.Fact(A.factSchema(failure), failure, A.directive(failure), F.sorted(B.controller(repair)))
    val saved = A.Saved(F.nativeRecord(79), fact)
    val completed = take(repair.acceptC(O.SemanticCompleted(repair.next.asInstanceOf[O.Semantic].key,
      "SPECIFICATION-ONLY accepted second primary Repair before reconstruction")))
    val (controller, direction, capture, current) = _inspection_source_from(completed, saved.reuse, applicability = true, prior = prior)
    (saved, controller, direction, capture, current)
  }

  /** A real pure failed-check transition follows the second primary repair and
    * its independently selected inspection source. Records remain spec-owned. */
  private def _post_validation_source_fixture: (A.Saved, O.State, GoalPhaseDependencySourceRevision.Direction,
    GoalPhaseDependencySourceRevision.Capture, C.Payload) = {
    import GoalPhaseDependencySourceRevision as R
    import GoalPhaseCanonicalDependencySourceSuccession as Source
    val (saved, inspecting, prior, previouscapture, previouscandidate) = _second_primary_failure_fixture
    val observed = take(inspecting.acceptC(O.CandidateObserved(previouscandidate.candidate.get)))
    val consumption = R.consumptionOf(previouscapture)
    val binding = R.bindingOf(previouscapture)
    val check = GoalPhaseNativeValidation.Check(observed.plan.steps(observed.cursor).checks.head, 1,
      WorkflowSbtValidation.Kind.Test)
    val program = consumption.programs.find(_.check == check.identity).get.program
    val output = "SPECIFICATION-ONLY later selected test failed\n"
    val process = WorkflowSbtCapture.decode(F.process(text = output, identity = program)).toOption.get
    val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1), output, "",
      Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Failed, None, None)), Some(process))
    val validation = GoalPhaseNativeValidation.Payload("98", previouscandidate.step, observed.candidate.get,
      Vector(check) ++ binding.checks.map(_.copy(dependency = Some(binding.identity))), Vector(receipt),
      Some("repair-required"), dependencySource = Some(previouscapture), sourceConsumption = Some(consumption))
    val context = take(W.checkFailureContextC(previouscandidate.changedPaths, previouscandidate.diff.get, validation))
    val repair = take(observed.acceptC(O.RepairableChecksObserved(observed.candidate.get, Vector(check.identity),
      "SPECIFICATION-ONLY later selected check receipt", O.RepairDirective("Correct the later failed check",
        Vector("Preserve primary source repairs and correct the selected assertion")))))
    val historical = Source.HistoricalFailure(ContextReference(prior.target.sessionId, "96"),
      ContextReference(prior.target.sessionId, "100"), context)
    val nextbinding = binding.copy(revision = binding.revision + 1)
    val proposal = prior.target.proposal.copy(binding = ContextReference(nextbinding.identity, nextbinding.revision.toString))
    val origin = prior.target.canonicalOrigin.get.copy(controller = GoalPhaseCandidateRecovery.controllerSnapshot(repair),
      originalDirection = Some(prior), previousDirection = None, inspectionFrontier = None,
      historicalFailure = Some(historical), historicalPublicationFailure = None,
      selection = prior.target.canonicalOrigin.get.selection.copy(source = proposal),
      baseLineage = Vector(O.HistoryCommit(binding.base.head, binding.base.tree, Vector.empty)))
    val target = prior.target.copy(sourceRevision = "102", pendingRevision = "102", generation = repair.generation,
      repairProgress = GoalPhaseRepairProgress.snapshot(repair), binding = nextbinding, proposal = proposal,
      canonicalOrigin = Some(origin))
    val direction = prior.copy(target = target, directive = ContextReference("post-check-source-human", "1"))
    val controller = take(repair.adoptCanonicalDependencySourceC(direction.directive))
    val capture = previouscapture.copy(sourceRevision = "106", generation = controller.generation, direction = direction)
    val candidate = previouscandidate.copy(sourceRevision = "106", dependencySourceDirection = Some(direction),
      dependencySourceCapture = Some(capture), diff = Some("SPECIFICATION-ONLY corrected later failed check"))
    (saved, controller, direction, capture, candidate)
  }

  "LSR-01 Canonical publication-failure succession" should {
    import LaterSourcePublicationSpecificationFixture as L
    "retain the original primary fact and latest Saved across a genuine later failed-check source edge" in {
      Given("CPB-243-SOURCE-LINEAGE-01: two pure primary reservations, inspection succession and an actual-shaped failed selected check")
      import GoalPhaseDependencySourceRevision as R
      import GoalPhaseCanonicalDependencySourceSuccession as Source
      val original = A.codec.encode(L.fact)
      val (saved, controller, direction, capture, candidate) = _post_validation_source_fixture
      val origin = direction.target.canonicalOrigin.get
      val failure = origin.historicalFailure.get
      val prior = origin.originalDirection.get
      val previousselected = direction.copy(target = direction.target.copy(canonicalOrigin = Some(origin.copy(
        originalDirection = Some(L.direction), previousDirection = Some(prior)))))
      val description = direction.copy(target = direction.target.copy(sourceRevision = failure.validation.revision,
        pendingRevision = failure.validation.revision))
      val progress = candidate.copy(candidate = None, changedPaths = Vector.empty, diff = None,
        stagedPaths = Vector.empty, dependencySourceGuard = None, dependencySourceCapture = None)
      When("the next canonical source follows the exact retained failed check and the existing Saved relation")
      val retained = A.canonicalPublicationFailure(direction)
      val reused = A.reusedCandidateC(saved, candidate, controller, F.nativeRecord(106))
      Then("the third ordinary reservation preserves both primary facts without activating publication failure or passing a check")
      Source.failureValid(failure) shouldBe true
      R.directionValid(direction) shouldBe true
      R.captureValid(capture) shouldBe true
      failure.context.validation.dependencySource.map(_.direction) shouldBe Some(prior)
      GoalPhaseNativeValidation.repairable(failure.context.validation) shouldBe true
      GoalPhaseNativeWorkContinuation.sourceCheckFailureMatches(direction, failure.context) shouldBe true
      retained shouldBe Some(L.historical)
      R.directionValid(previousselected) shouldBe true
      A.canonicalPublicationFailure(previousselected) shouldBe retained
      R.directionValid(description) shouldBe true
      A.canonicalPublicationFailure(description) shouldBe retained
      saved.reference should not be L.saved.reference
      retained.map(_.repair) should not be Some(saved.reference)
      A.codec.encode(L.fact) shouldBe original
      A.canonicalSelectionValid(saved, Some(direction), None, controller) shouldBe true
      A.canonicalSourceSuccessorValid(saved.fact.failure.candidate, candidate) shouldBe true
      C.sourceCaptureValid(candidate) shouldBe true
      C.sourceCaptureValid(progress) shouldBe true
      C.codec.decode(C.codec.encode(candidate)) shouldBe Right(candidate)
      direction.target.asJson.as[R.Target] shouldBe Right(direction.target)
      reused.isSuccess shouldBe true
      A.reusedCandidateC(saved, progress, controller, F.nativeRecord(106)).isSuccess shouldBe true
      candidate.primaryPublicationRepair shouldBe Some(saved.reuse)
      (controller.repairs, controller.ordinaryRepairs) shouldBe (3, 3)
      controller.plan shouldBe saved.fact.failure.plan
      controller.checksReceipt shouldBe None
      origin.historicalPublicationFailure shouldBe None
      origin.inspectionFrontier shouldBe None
      A.canonicalCauseAt(saved, Some(direction), controller) shouldBe false
      take(A.canonicalWorkContextC(saved, Some(direction), controller, "RepairStep")) shouldBe None
      take(A.workContextC(saved.fact, saved.reference, controller, "RepairStep")) shouldBe None
    }

    "refuse crossed failed-check provenance record order and native reuse without borrowing earlier primary authority" in {
      Given("CPB-243-SOURCE-LINEAGE-01: the exact checked source edge and latest Saved under unchanged finite repair policy")
      import GoalPhaseDependencySourceRevision as R
      val (saved, controller, direction, capture, candidate) = _post_validation_source_fixture
      val target = direction.target
      val origin = target.canonicalOrigin.get
      val failure = origin.historicalFailure.get
      val validation = failure.context.validation
      val priorcapture = validation.dependencySource.get
      val crossedsource = priorcapture.copy(direction = priorcapture.direction.copy(
        directive = ContextReference("foreign-source-human", "1")))
      def _failure_direction_(value: GoalPhaseCanonicalDependencySourceSuccession.HistoricalFailure): R.Direction =
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(historicalFailure = Some(value)))))
      def _validation_direction_(value: GoalPhaseNativeValidation.Payload): R.Direction =
        _failure_direction_(failure.copy(context = failure.context.copy(validation = value)))
      val crossedcaptures = Vector(priorcapture.copy(direction = L.direction), priorcapture.copy(sessionId = "foreign-session"),
        priorcapture.copy(plan = ContextReference("foreign-plan", "1")), priorcapture.copy(step = "foreign-step"),
        priorcapture.copy(primaryBase = priorcapture.primaryBase.copy(tree = "f" * 40)),
        priorcapture.copy(sourceRevision = "97"), priorcapture.copy(generation = target.generation))
      val crossedchecks = Vector(validation.copy(dependencySource = None), validation.copy(outcome = None),
        validation.copy(outcome = Some("passed")), validation.copy(step = "foreign-step"),
        validation.copy(selected = validation.selected.tail), validation.copy(receipts = Vector.empty),
        validation.copy(receipts = validation.receipts.map(_.copy(exitCode = Some(0)))),
        validation.copy(sourceRevision = "96"), validation.copy(sourceRevision = "101"))
      val crossedcanonical = validation.copy(dependencySource = Some(crossedsource),
        sourceConsumption = Some(R.consumptionOf(crossedsource)))
      val crossedfacts = Vector(failure.copy(candidate = failure.candidate.copy(identity = "foreign-session")),
        failure.copy(candidate = failure.candidate.copy(revision = "93")),
        failure.copy(candidate = failure.candidate.copy(revision = "98")),
        failure.copy(validation = failure.validation.copy(revision = "97")),
        failure.copy(validation = failure.validation.copy(revision = "103")))
      val crossedtargets = Vector(target.copy(sessionId = "foreign-session"), target.copy(step = "foreign-step"),
        target.copy(plan = target.plan.copy(revision = "foreign-plan")),
        target.copy(primaryBase = target.primaryBase.copy(tree = "f" * 40)),
        target.copy(generation = priorcapture.generation),
        target.copy(repairProgress = target.repairProgress.copy(automaticCount = 0, ordinaryCount = 0)),
        target.copy(repairProgress = target.repairProgress.copy(limit = target.repairProgress.limit + 1)),
        target.copy(repairProgress = target.repairProgress.copy(selectedLimit = Some(99), effectiveLimit = Some(99))),
        target.copy(binding = target.binding.copy(checks = Vector.empty)),
        target.copy(canonicalOrigin = Some(origin.copy(participant = "foreign-participant"))),
        target.copy(canonicalOrigin = Some(origin.copy(componentInstance = "foreign-owner"))),
        target.copy(canonicalOrigin = Some(origin.copy(primaryRoot = "/specification/foreign"))),
        target.copy(canonicalOrigin = Some(origin.copy(historicalFailure = None))),
        target.copy(canonicalOrigin = Some(origin.copy(originalDirection = Some(L.direction)))))
      val directions = crossedcaptures.map(value => _validation_direction_(validation.copy(dependencySource = Some(value)))) ++
        (crossedchecks :+ crossedcanonical).map(_validation_direction_) ++ crossedfacts.map(_failure_direction_) ++
        crossedtargets.map(value => direction.copy(target = value))
      When("the retained-cause selection and structural candidate relation inspect each crossed edge")
      val retained = directions.map(A.canonicalPublicationFailure)
      val selected = directions.map(value => A.canonicalSelectionValid(saved, Some(value), None, controller))
      val candidates = directions.map(value => candidate.copy(dependencySourceDirection = Some(value),
        dependencySourceCapture = Some(capture.copy(direction = value))))
      val sources = candidates.map(C.sourceCaptureValid)
      Then("no crossed edge can revive the old cause, authenticate a candidate or reset the actual controller reservation")
      R.captureValid(crossedsource) shouldBe true
      all(retained) shouldBe None
      all(selected) shouldBe false
      all(sources) shouldBe false
      A.reusedCandidateC(L.saved, candidate, controller, F.nativeRecord(106)).isSuccess shouldBe false
      A.reusedCandidateC(saved, candidate.copy(primaryPublicationRepair = Some(L.saved.reuse)), controller,
        F.nativeRecord(106)).isSuccess shouldBe false
      A.reusedCandidateC(saved, candidate, controller, F.nativeRecord(105)).isSuccess shouldBe false
      val crossedrecord = F.nativeRecord(106)
      A.reusedCandidateC(saved, candidate, controller, crossedrecord.copy(history = crossedrecord.history.drop(1)))
        .isSuccess shouldBe false
      A.reusedCandidateC(saved, candidate, L.successor, F.nativeRecord(106)).isSuccess shouldBe false
      C.sourceCaptureValid(candidate.copy(dependencySourceCapture = Some(priorcapture))) shouldBe false
      A.canonicalSourceSuccessorValid(saved.fact.failure.candidate, candidate.copy(sourceRevision = "94")) shouldBe false
      A.canonicalCauseAt(saved, Some(direction), controller) shouldBe false
    }

    "select the actual second primary repair through checked inspection ancestry while retaining the first historical cause" in {
      Given("two classified pure primary Compile failures, each reserving its real ordinary Repair, followed by source reconstruction")
      import GoalPhaseDependencySourceRevision as R
      val (saved, controller, direction, capture, candidate) = _second_primary_failure_fixture
      val previous = saved.fact.failure.candidate
      val progress = candidate.copy(candidate = None, changedPaths = Vector.empty, diff = None,
        stagedPaths = Vector.empty, dependencySourceGuard = None, dependencySourceCapture = None)
      When("source selection, the structural codec and actual Saved reuse check consume the independently selected next capture")
      val historical = A.canonicalPublicationFailure(direction)
      val continued = A.reusedCandidateC(saved, candidate, controller, F.nativeRecord(94))
      val preparing = A.reusedCandidateC(saved, progress, controller, F.nativeRecord(94))
      val publictarget = R.decodePublicTarget(GoalPhaseNativeMetadata.record(direction.target.asJson).toJsonStringC.toOption
        .flatMap(io.circe.parser.parse(_).toOption).get)
      val stalepolicy = A.processPolicyMatches(previous, candidate, L.historical.fact.failure.processPolicy,
        direction.target.canonicalOrigin.get.processPolicy)
      Then("Saved2 remains the current repair while historical Fact1 and its original publication stay literal")
      A.factValid(saved.fact) shouldBe true
      A.failureKind(saved.fact.failure) shouldBe Some(A.FailureKind.Compile)
      saved.reference should not be L.saved.reference
      saved.fact.failure.failed should not be L.failed
      saved.fact.failure.candidate.dependencySourceDirection shouldBe direction.target.canonicalOrigin.get.originalDirection
      saved.fact.failure.candidate.dependencySourceCapture.map(_.direction) shouldBe previous.dependencySourceDirection
      R.directionValid(direction) shouldBe true
      R.captureValid(capture) shouldBe true
      publictarget shouldBe Right(direction.target)
      direction.target.asJson.as[R.Target] shouldBe Right(direction.target)
      historical shouldBe Some(L.historical)
      historical.map(_.repair) should not be Some(saved.reference)
      A.canonicalSelectionValid(saved, Some(direction), None, controller) shouldBe true
      continued.isSuccess shouldBe true
      preparing.isSuccess shouldBe true
      C.sourceCaptureValid(previous) shouldBe true
      C.sourceCaptureValid(candidate) shouldBe true
      C.sourceCaptureValid(progress) shouldBe true
      C.codec.decode(C.codec.encode(candidate)) shouldBe Right(candidate)
      A.canonicalSourceSuccessorValid(previous, candidate) shouldBe true
      A.currentSourceSuccessorValid(previous, candidate) shouldBe true
      saved.fact.failure.processPolicy should not be L.historical.fact.failure.processPolicy
      stalepolicy shouldBe false
      A.processPolicyMatches(previous, candidate, saved.fact.failure.processPolicy, direction.target.canonicalOrigin.get.processPolicy) shouldBe true
      candidate.primaryPublicationRepair shouldBe Some(saved.reuse)
      saved.reuse.sourceRevision shouldBe L.saved.reuse.sourceRevision
      saved.reuse.generation shouldBe L.capture.generation
      (controller.repairs, controller.ordinaryRepairs) shouldBe (2, 2)
      A.currentCauseAt(saved.fact, controller) shouldBe false
      A.canonicalCauseAt(saved, Some(direction), controller) shouldBe false
      take(A.canonicalWorkContextC(saved, Some(direction), controller, "RepairStep")) shouldBe None
      take(A.workContextC(saved.fact, saved.reference, controller, "RepairStep")) shouldBe None
      A.reusedCandidateC(L.saved, candidate, controller, F.nativeRecord(94)).isSuccess shouldBe false
      A.reusedCandidateC(saved, candidate.copy(primaryPublicationRepair = Some(L.saved.reuse)), controller,
        F.nativeRecord(94)).isSuccess shouldBe false
    }
    "reject crossed latest facts, prior captures, reconstruction edges and fresh reuse bounds without changing the historical cause" in {
      Given("the same two real pure primary reservations and their valid prior canonical capture and later inspection Direction")
      import GoalPhaseDependencySourceRevision as R
      val (saved, controller, direction, capture, candidate) = _second_primary_failure_fixture
      val previous = saved.fact.failure.candidate
      val target = direction.target
      val origin = target.canonicalOrigin.get
      val reuse = saved.reuse
      val wrongfacts = Vector(saved.copy(fact = saved.fact.copy(failure = saved.fact.failure.copy(
        candidate = previous.copy(sourceRevision = "95")))),
        saved.copy(fact = saved.fact.copy(failure = saved.fact.failure.copy(candidate = previous.copy(dependencySourceCapture = None)))),
        saved.copy(fact = saved.fact.copy(failure = saved.fact.failure.copy(participant = "foreign"))),
        saved.copy(fact = saved.fact.copy(failure = saved.fact.failure.copy(sessionId = "foreign"))),
        saved.copy(fact = saved.fact.copy(failure = saved.fact.failure.copy(base = O.Snapshot("d" * 40, "e" * 40)))),
        saved.copy(fact = saved.fact.copy(failure = saved.fact.failure.copy(plan = target.plan.copy(revision = "foreign")))),
        saved.copy(fact = saved.fact.copy(failure = saved.fact.failure.copy(generation = target.generation))),
        saved.copy(record = F.nativeRecord(76)), saved.copy(record = F.nativeRecord(95)))
      val directions = Vector(L.direction,
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(inspectionFrontier = None)))),
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(originalDirection = Some(L.olddirection))))),
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(primaryRoot = "/specification/foreign")))),
        direction.copy(target = target.copy(sessionId = "foreign")), direction.copy(participant = "foreign"),
        direction.copy(target = target.copy(step = "foreign")),
        direction.copy(target = target.copy(primaryBase = O.Snapshot("d" * 40, "e" * 40))),
        direction.copy(target = target.copy(plan = target.plan.copy(revision = "foreign"))),
        direction.copy(target = target.copy(completed = Vector.empty)),
        direction.copy(target = target.copy(binding = target.binding.copy(identity = "foreign"))),
        direction.copy(target = target.copy(sourceRevision = "70", pendingRevision = "70")),
        direction.copy(target = target.copy(generation = L.target.generation)),
        direction.copy(target = target.copy(repairProgress = target.repairProgress.copy(ordinaryCount = 0))),
        direction.copy(target = target.copy(repairProgress = target.repairProgress.copy(runtimePolicy = None,
          selectedLimit = None, effectiveLimit = None))))
      val reuses = Vector(reuse.copy(repair = ContextReference("foreign", reuse.repair.revision)),
        reuse.copy(repair = reuse.repair.copy(revision = "95")), reuse.copy(candidateRecordRevision = "70"),
        reuse.copy(candidateRecordRevision = reuse.repair.revision), reuse.copy(sourceRevision = previous.sourceRevision),
        reuse.copy(generation = L.target.generation), reuse.copy(generation = target.generation))
      val invalid = reuses.map(value => candidate.copy(primaryPublicationRepair = Some(value))) ++ Vector(
        candidate.copy(sourceRevision = "79"), candidate.copy(step = "foreign"),
        candidate.copy(dependencySourceCapture = Some(capture.copy(generation = capture.generation + 1))),
        candidate.copy(dependencySourceCapture = Some(capture.copy(direction = L.direction))),
        candidate.copy(dependencySourceCapture = Some(capture.copy(sourceRevision = "95"))),
        candidate.copy(dependencySourceCapture = Some(capture.copy(sessionId = "foreign"))),
        candidate.copy(dependencySourceDirection = Some(L.direction)))
      val uncaptured = candidate.copy(candidate = None, changedPaths = Vector.empty, diff = None,
        stagedPaths = Vector.empty, dependencySourceGuard = None, dependencySourceCapture = None,
        dependencySourceDirection = Some(directions(1)))
      When("the exact latest native reuse, checked predecessor and structural capture checks receive each crossing")
      val rejectedfacts = wrongfacts.map(value => A.reusedCandidateC(value, candidate, controller, F.nativeRecord(94)).isSuccess)
      val rejectedselection = directions.map(value => A.canonicalSelectionValid(saved, Some(value), None, controller))
      val rejectedreuse = invalid.map(value => A.reusedCandidateC(saved, value, controller, F.nativeRecord(94)).isSuccess)
      Then("no crossed Saved, source edge, revision or capture can substitute for the actual current repair")
      all(rejectedfacts) shouldBe false
      all(rejectedselection) shouldBe false
      all(rejectedreuse) shouldBe false
      all(invalid.map(C.sourceCaptureValid)) shouldBe false
      C.sourceCaptureValid(uncaptured) shouldBe false
      A.canonicalSourceSuccessorValid(previous.copy(dependencySourceCapture = None), candidate) shouldBe false
      A.canonicalSourceSuccessorValid(previous.copy(dependencySourceDirection = Some(L.olddirection)), candidate) shouldBe false
      A.canonicalSourceSuccessorValid(previous.copy(sourceRevision = "95"), candidate) shouldBe false
      A.processPolicyMatches(previous, candidate, "{}", origin.processPolicy) shouldBe false
      A.processPolicyMatches(previous, candidate, saved.fact.failure.processPolicy, "{}") shouldBe false
      A.canonicalPublicationFailure(direction) shouldBe Some(L.historical)
      A.canonicalSelectionValid(saved, Some(direction), None, L.successor) shouldBe false
    }
    "preserve the exact historical publication cause after a subsequent ordinary repair and applicability inspection checkpoint" in {
      Given("one original publication Repair and a later failed check followed by its accepted ordinary Repair before reconstruction")
      import GoalPhaseDependencySourceRevision as R
      val (controller, direction, capture, observed) = _inspection_source_fixture_for(subsequentrepair = true, applicability = true)
      val checkpoint = O.Snapshot("a" * 40, "b" * 40)
      val tip = controller.tipSnapshot
      val proof = O.CandidateContinuation(checkpoint, Vector(
        O.HistoryCommit(checkpoint.head, checkpoint.tree, Vector(tip.head)),
        O.HistoryCommit(tip.head, tip.tree, Vector.empty)))
      val work = GoalPhaseCommittedStepWork.Work(ContextReference("same-session", "95"), tip, proof,
        L.candidate.changedPaths, L.candidate.diff.get)
      val candidate = observed.copy(candidate = Some(checkpoint), continuation = Some(proof),
        changedPaths = Vector.empty, diff = Some(""), committedWork = Some(work))
      When("the authenticated-shaped reconstructed source and checkpoint candidate follow the historical publication ancestry")
      val historical = A.canonicalPublicationFailure(direction)
      val continued = A.reusedCandidateC(L.saved, candidate, controller, F.nativeRecord(94))
      val context = take(A.canonicalWorkContextC(L.saved, Some(direction), controller, "RepairStep"))
      Then("the original cause remains exact while current counts and inspection authority remain unchanged")
      R.directionValid(direction) shouldBe true
      R.captureValid(capture) shouldBe true
      R.candidateValid(candidate) shouldBe true
      C.sourceCaptureValid(candidate) shouldBe true
      GoalPhaseCommittedStepWork.bound(candidate, work) shouldBe true
      C.codec.decode(C.codec.encode(candidate)) shouldBe Right(candidate)
      direction.target.schemaVersion shouldBe R.canonicalApplicabilitySchema
      direction.target.canonicalOrigin.get.selection.schemaVersion shouldBe
        GoalPhaseCanonicalDependencySourceSuccession.applicabilityProposalSchema
      direction.target.canonicalOrigin.get.selection.publication.result.publication shouldBe L.publication.result.publication
      direction.target.canonicalOrigin.get.selection.publication.result.applicability.map(_.current) shouldBe
        Some(LocalPublication.Source(direction.target.binding.base.head, direction.target.binding.base.tree, Vector.empty))
      historical shouldBe Some(L.historical)
      A.canonicalSelectionValid(L.saved, Some(direction), None, controller) shouldBe true
      continued.isSuccess shouldBe true
      A.currentSourceSuccessorValid(L.failedcandidate, candidate) shouldBe true
      A.processPolicyMatches(L.failedcandidate, candidate, L.failure.processPolicy,
        direction.target.canonicalOrigin.get.processPolicy) shouldBe true
      A.canonicalCauseAt(L.saved, Some(direction), controller) shouldBe false
      context shouldBe None
      controller.stage shouldBe O.Stage.InspectCandidate
      controller.repairs shouldBe 2
      controller.ordinaryRepairs shouldBe 2
      direction.target.repairProgress shouldBe GoalPhaseRepairProgress.snapshot(controller)
      L.direction.target.repairProgress.automaticCount shouldBe 1
      L.direction.target.repairProgress.ordinaryCount shouldBe 1
      controller.plan shouldBe L.successor.plan
      controller.base shouldBe L.successor.base
      controller.completed shouldBe L.successor.completed
      candidate.primaryPublicationRepair shouldBe Some(L.saved.reuse)
    }
    "reject counter resets, changed repair policy and crossed reconstructed lineage after ordinary progress" in {
      Given("a valid v2 applicability inspection with two ordinary repairs and its exact earlier publication cause at one repair")
      import GoalPhaseDependencySourceRevision as R
      val (controller, direction, capture, candidate) = _inspection_source_fixture_for(subsequentrepair = true, applicability = true)
      val target = direction.target
      val origin = target.canonicalOrigin.get
      val progress = target.repairProgress
      def _with_progress_(value: GoalPhaseRepairProgress.Snapshot): R.Direction = direction.copy(target = target.copy(
        repairProgress = value, canonicalOrigin = Some(origin.copy(controller = origin.controller.copy(
          repairs = value.automaticCount, ordinaryRepairs = value.ordinaryCount)))))
      val resetautomatic = _with_progress_(progress.copy(automaticCount = 0, ordinaryCount = 0))
      val resetordinary = _with_progress_(progress.copy(ordinaryCount = 0))
      val legacy = _with_progress_(progress.copy(runtimePolicy = None, selectedLimit = None, effectiveLimit = None))
      val selectedlimit = if (progress.selectedLimit.contains(64)) 63 else 64
      val changedselection = _with_progress_(progress.copy(selectedLimit = Some(selectedlimit),
        effectiveLimit = Some(math.min(selectedlimit, GoalPhaseRuntimeRepairControl.ceiling))))
      val changedlimit = _with_progress_(progress.copy(limit = if (progress.limit == 64) 63 else progress.limit + 1))
      val changedeffective = _with_progress_(progress.copy(effectiveLimit = progress.effectiveLimit.map(n => if (n == 0) 1 else n - 1)))
      val crossedfact = L.historical.copy(fact = L.fact.copy(failure = L.failure.copy(participant = "foreign")))
      val crossedprior = L.direction.copy(target = L.target.copy(canonicalOrigin = Some(
        L.origin.copy(historicalPublicationFailure = Some(crossedfact)))))
      val crossed = Vector(
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(inspectionFrontier = None)))),
        direction.copy(target = target.copy(sourceRevision = L.target.sourceRevision, pendingRevision = L.target.pendingRevision)),
        direction.copy(target = target.copy(generation = L.target.generation)),
        direction.copy(target = target.copy(sessionId = "foreign")),
        direction.copy(participant = "foreign"),
        direction.copy(target = target.copy(primaryBase = O.Snapshot("d" * 40, "e" * 40))),
        direction.copy(target = target.copy(plan = target.plan.copy(revision = "foreign"))),
        direction.copy(target = target.copy(completed = Vector.empty)),
        direction.copy(target = target.copy(binding = target.binding.copy(base = O.Snapshot("d" * 40, "e" * 40)))),
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(componentInstance = "foreign")))),
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(originalDirection = Some(crossedprior))))))
      val invalid = Vector(resetautomatic, resetordinary, legacy, changedselection, changedlimit, changedeffective) ++ crossed
      When("the historical lookup and exact current selection evaluate each independently changed snapshot or lineage")
      val historical = invalid.map(A.canonicalPublicationFailure)
      val selected = invalid.map(value => A.canonicalSelectionValid(L.saved, Some(value), None, controller))
      Then("neither monotonic history nor current selection accepts a reset, policy change or crossed provenance")
      R.directionValid(direction) shouldBe true
      R.captureValid(capture) shouldBe true
      R.directionValid(resetautomatic) shouldBe true
      R.directionValid(resetordinary) shouldBe true
      GoalPhaseRepairProgress.validateC(legacy.target.repairProgress).isSuccess shouldBe true
      GoalPhaseRepairProgress.validateC(changedselection.target.repairProgress).isSuccess shouldBe true
      all(historical) shouldBe None
      all(selected) shouldBe false
      A.canonicalSelectionValid(L.saved, Some(direction), None, L.successor) shouldBe false
      A.canonicalSelectionValid(L.saved.copy(fact = L.fact.copy(failure = L.failure.copy(sessionId = "foreign"))),
        Some(direction), None, controller) shouldBe false
      A.currentSourceSuccessorValid(L.failedcandidate, candidate.copy(dependencySourceCapture = Some(
        capture.copy(direction = L.direction)))) shouldBe false
      A.processPolicyMatches(L.failedcandidate, candidate, L.failure.processPolicy, "{}") shouldBe false
      A.canonicalCauseAt(L.saved, Some(direction), controller) shouldBe false
      take(A.canonicalWorkContextC(L.saved, Some(direction), controller, "RepairStep")) shouldBe None
    }
    "retain the exact historical cause through the declared non-input applicability successor variant" in {
      Given("the same retained classified failure and an explicitly versioned source applicability selection")
      val previous = L.publication.result.publication.copy(request = L.publication.result.publication.request.copy(
        source = LocalPublication.Source("a" * 40, "b" * 40, Vector.empty)))
      val fact = CommittedNoninputApplicabilitySpecificationFixture.fact(previous,
        L.publication.result.publication.request.source, L.binding.root, L.binding.branch,
        L.publication.destination, L.publication.artifact)
      val observation = L.publication.copy(result = L.publication.result.copy(publication = previous, applicability = Some(fact)))
      val selection = L.proposal.copy(schemaVersion = GoalPhaseCanonicalDependencySourceSuccession.applicabilityProposalSchema,
        publication = observation)
      val target = L.target.copy(schemaVersion = GoalPhaseDependencySourceRevision.canonicalApplicabilitySuccessorSchema,
        canonicalOrigin = Some(L.origin.copy(selection = selection)))
      val direction = L.direction.copy(target = target)
      When("the existing cause lookup and active Repair generation checks consume the new successor schema")
      val inherited = A.canonicalPublicationFailure(direction)
      val context = take(A.canonicalWorkContextC(L.saved, Some(direction), L.successor, "RepairStep"))
      Then("cause identity, owner, generation and original publication remain exact without another reservation")
      GoalPhaseDependencySourceRevision.directionValid(direction) shouldBe true
      inherited shouldBe Some(L.historical)
      A.canonicalCauseAt(L.saved, Some(direction), L.successor) shouldBe true
      context.map(_.publication) shouldBe Some(L.failed)
      A.canonicalCauseAt(L.saved.copy(record = F.nativeRecord(64)), Some(direction), L.successor) shouldBe false
      A.canonicalCauseAt(L.saved, Some(direction), L.repair) shouldBe false
    }
    "carry the exact historical failure through reconstructed inspection without creating another active Repair" in {
      Given("an accepted current Repair, same-Plan clean checkpoint and separately selected canonical inspection source")
      import GoalPhaseDependencySourceRevision as R
      val (controller, direction, capture, candidate) = _inspection_source_fixture
      When("the owning capture and publication policy relations follow the native-authenticated source ancestry")
      val inherited = A.canonicalPublicationFailure(direction)
      val selected = A.canonicalSelectionValid(L.saved, Some(direction), None, controller)
      val continued = A.reusedCandidateC(L.saved, candidate, controller, F.nativeRecord(94))
      val work = A.canonicalWorkContextC(L.saved, Some(direction), controller, "RepairStep")
      Then("the original exact failure supports fresh capture while inspection stays inspection and grants no new active cause")
      R.directionValid(direction) shouldBe true
      R.captureValid(capture) shouldBe true
      R.candidateValid(candidate) shouldBe true
      inherited shouldBe Some(L.historical)
      selected shouldBe true
      continued.isSuccess shouldBe true
      A.canonicalSourceSuccessorValid(L.failedcandidate, candidate) shouldBe true
      C.sourceCaptureValid(candidate) shouldBe true
      direction.target.canonicalOrigin.get.processPolicy should not be L.failure.processPolicy
      A.processPolicyMatches(L.failedcandidate, candidate, L.failure.processPolicy, direction.target.canonicalOrigin.get.processPolicy) shouldBe true
      A.canonicalCauseAt(L.saved, Some(direction), controller) shouldBe false
      take(work) shouldBe None
      direction.target.canonicalOrigin.get.historicalPublicationFailure shouldBe None
      direction.target.canonicalOrigin.get.originalDirection shouldBe Some(L.direction)
      controller.stage shouldBe O.Stage.InspectCandidate
      controller.plan shouldBe L.repair.plan
      controller.base shouldBe L.repair.base
      controller.completed shouldBe L.repair.completed
      GoalPhaseRepairProgress.snapshot(controller) shouldBe GoalPhaseRepairProgress.snapshot(L.repair)
      candidate.primaryPublicationRepair shouldBe Some(L.saved.reuse)
    }
    "refuse missing crossed stale or reset inspection ancestry instead of borrowing an unrelated saved cause" in {
      Given("the exact historical failure and a valid reconstructed source Direction with its current capture")
      import GoalPhaseDependencySourceRevision as R
      val (controller, direction, capture, candidate) = _inspection_source_fixture
      val target = direction.target
      val origin = target.canonicalOrigin.get
      val prior = L.direction
      val crossedcause = L.historical.copy(repair = ContextReference("foreign", "63"))
      val crossedprior = prior.copy(target = prior.target.copy(canonicalOrigin = Some(
        prior.target.canonicalOrigin.get.copy(historicalPublicationFailure = Some(crossedcause)))))
      val variants = Vector(
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(originalDirection = None)))),
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(inspectionFrontier = None)))),
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(originalDirection = Some(L.olddirection))))),
        direction.copy(target = target.copy(sourceRevision = prior.target.sourceRevision, pendingRevision = prior.target.pendingRevision)),
        direction.copy(target = target.copy(generation = prior.target.generation)),
        direction.copy(target = target.copy(sessionId = "foreign")),
        direction.copy(target = target.copy(primaryBase = O.Snapshot("d" * 40, "e" * 40))),
        direction.copy(target = target.copy(step = "foreign")),
        direction.copy(target = target.copy(plan = target.plan.copy(revision = "foreign"))),
        direction.copy(target = target.copy(repairProgress = target.repairProgress.copy(ordinaryCount = 0))),
        direction.copy(target = target.copy(canonicalOrigin = Some(origin.copy(originalDirection = Some(crossedprior))))))
      When("each changed graph and each crossed current capture is checked against the single owning historical-cause relation")
      val inherited = variants.map(A.canonicalPublicationFailure)
      val selections = variants.map(value => A.canonicalSelectionValid(L.saved, Some(value), None, controller))
      val mixed = Vector(candidate.copy(primaryPublicationRepair = Some(L.saved.reuse.copy(repair = ContextReference("foreign", "63")))),
        candidate.copy(sourceRevision = target.sourceRevision),
        candidate.copy(dependencySourceCapture = Some(capture.copy(generation = capture.generation + 1))),
        candidate.copy(dependencySourceCapture = Some(capture.copy(direction = L.direction))),
        candidate.copy(dependencySourceDirection = Some(L.direction)))
      Then("no mismatched graph or capture authorizes reuse, policy changes or an active cause")
      inherited.foreach(_ shouldBe None)
      selections.foreach(_ shouldBe false)
      mixed.foreach(value => A.canonicalSourceSuccessorValid(L.failedcandidate, value) shouldBe false)
      A.canonicalSelectionValid(L.saved.copy(fact = L.fact.copy(failure = L.failure.copy(sessionId = "foreign"))),
        Some(direction), None, controller) shouldBe false
      A.processPolicyMatches(L.failedcandidate, candidate, "{}", origin.processPolicy) shouldBe false
      A.processPolicyMatches(L.failedcandidate, candidate, L.failure.processPolicy, "{}") shouldBe false
      R.directionValid(direction) shouldBe true
    }
    "carry only the exact saved publication cause through one canonical generation without another reservation" in {
      Given("a real classified pure Compile failure and its exact ordinary reservation after an accepted earlier source Step")
      When("canonical adoption adds its one generation and the context owner authenticates the retained relation")
      val context = A.canonicalWorkContextC(L.saved, Some(L.direction), L.successor, "RepairStep")
      val ordinary = A.workContextC(L.fact, L.saved.reference, L.successor, "RepairStep")
      Then("the genuine publication remains the cause while the generic old generation contract and accounting stay intact")
      A.currentCauseAt(L.fact, L.repair) shouldBe true
      A.currentCauseAt(L.fact, L.successor) shouldBe false
      A.canonicalCauseAt(L.saved, Some(L.direction), L.successor) shouldBe true
      take(context).get.publication shouldBe L.failed
      take(context).get.testsExecuted shouldBe false
      take(ordinary) shouldBe None
      take(A.canonicalWorkContextC(L.saved, Some(L.direction), L.successor, "ImplementStep")) shouldBe None
      GoalPhaseRepairProgress.snapshot(L.successor) shouldBe GoalPhaseRepairProgress.snapshot(L.repair)
      Vector(take(L.successor.adoptCanonicalDependencySourceC(ContextReference("another-source", "1"))),
        L.repair, F.repair, CanonicalDependencySourceSpecificationFixture.controller).foreach(controller =>
        A.canonicalCauseAt(L.saved, Some(L.direction), controller) shouldBe false)
      A.canonicalCauseAt(L.saved.copy(record = F.nativeRecord(64)), Some(L.direction), L.successor) shouldBe false
    }
    "capture fresh current source when the failed later-Step candidate had no source capture" in {
      Given("the old candidate is source-less because its standing direction belonged to an already accepted Step")
      val inspection = take(L.successor.acceptC(O.SemanticCompleted(L.successor.next.asInstanceOf[O.Semantic].key,
        "SPECIFICATION-ONLY fresh current repair")))
      When("partial inspection and the completed current capture are checked against the exact saved failure")
      val partial = A.reusedCandidateC(L.saved, L.fresh, inspection, F.nativeRecord(74))
      val complete = A.reusedCandidateC(L.saved, L.candidate, inspection, F.nativeRecord(74))
      Then("the new owner capture is required and no historical capture is manufactured or reused")
      L.failedcandidate.dependencySourceCapture shouldBe None
      A.canonicalSelectionValid(L.saved, Some(L.direction), None, inspection) shouldBe true
      partial.isSuccess shouldBe true
      complete.isSuccess shouldBe true
      C.sourceCaptureValid(L.candidate) shouldBe true
      A.currentSourceSuccessorValid(L.failedcandidate, L.candidate) shouldBe true
      Vector(L.candidate.copy(primaryPublicationRepair = Some(L.saved.reuse.copy(repair = ContextReference("foreign", "63")))),
        L.candidate.copy(sourceRevision = "70"), L.candidate.copy(dependencySourceCapture = Some(L.capture.copy(generation = L.capture.generation + 1))),
        L.candidate.copy(dependencySourceDirection = Some(L.olddirection))).foreach(candidate =>
          A.reusedCandidateC(L.saved, candidate, inspection, F.nativeRecord(74)).isSuccess shouldBe false)
      A.processPolicyMatches(L.failedcandidate, L.candidate, L.failure.processPolicy, L.origin.processPolicy) shouldBe true
      A.processPolicyMatches(L.failedcandidate, L.candidate, L.failure.processPolicy, "{}") shouldBe false
      A.publicationRoute(L.failure, A.scope(L.failure), Vector.empty, L.candidate, "76", L.successor.generation) should not be (A.PublicationRoute.FirstFailureSuccessor)
    }
    "retain strict genuine generation compile and termination classifications inside opaque publication provenance" in {
      Given("the existing complete generation signature, Compile signature and a new exact publication provenance value")
      val generationfailure = L.failure.copy(failed = L.failed.copy(process = Some(F.process(F.generationOutput))))
      val generationrepair = take(A.stateC(L.validation, generationfailure))
      val generationfact = A.Fact(A.generationSchema, generationfailure, A.directive(generationfailure), F.sorted(B.controller(generationrepair)))
      val historical = GoalPhaseCanonicalDependencySourceSuccession.HistoricalPublicationFailure(L.saved.reference, generationfact)
      When("the owner codec receives a genuine generation cause or incomplete unknown live and crossed captures")
      val invalid = Vector(L.failed.copy(status = "unknown"),
        L.failed.copy(process = Some(F.process(termination = ProcessExecutionTermination.TerminationUnconfirmed))),
        L.failed.copy(process = Some(F.process(truncated = true))),
        L.failed.copy(process = Some(F.process(identity = "foreign-program"))),
        L.failed.copy(process = Some(F.process(text = "[error] (Test / test) Tests unsuccessful\n"))))
      Then("only a formally ended classified failure remains publication provenance and none becomes a check failure")
      A.failureKind(generationfailure) shouldBe Some(A.FailureKind.Generation)
      historical.asJson.as[GoalPhaseCanonicalDependencySourceSuccession.HistoricalPublicationFailure] shouldBe Right(historical)
      invalid.foreach(failed => GoalPhaseCanonicalDependencySourceSuccession.publicationFailureValid(
        L.historical.copy(fact = L.fact.copy(failure = L.failure.copy(failed = failed)))) shouldBe false)
    }
  }

  "E14 primary publication generation repair" should afterWord(
    "in spec:goal-phase-prerequisite-publication-recovery, example:E14, rules:RGEN01, phase:6.11") {
    "classify only the exact terminated missing INPUT and empty generated-output failure" in {
      Given("a pure complete typed publication capture with the retained cozyGenerate summary and both required diagnostics")
      val failed = F.generationFailure.failed
      val before = failed.asJson
      When("the distinct generation and legacy Compile classifiers inspect the same retained process")
      val generation = A.generationFailure(failed, F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target))
      val compile = A.compileFailure(failed, F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target))
      Then("generation qualifies without becoming Compile or changing a retained byte")
      generation shouldBe true
      compile shouldBe false
      A.failureKind(F.generationFailure) shouldBe Some(A.FailureKind.Generation)
      A.failureValid(F.generationFailure) shouldBe true
      failed.asJson shouldBe before
      GoalPhasePrerequisitePublicationUnknownRecovery.bindingC(F.candidate).isSuccess shouldBe false
    }

    "refuse other tasks partial captures unknown outcomes and crossed publication bindings" in {
      Given("the exact generation signature crossed with other task summaries effects and typed termination states")
      val failed = F.generationFailure.failed
      val outputs = Vector(F.output, "[error] (Compile / doc) Scaladoc generation failed\n",
        F.generationOutput.replace("Error[No input]", "Error[Other input]"),
        F.generationOutput.replace("\"actual\":\"none\"", "\"actual\":\"changed artifact\""),
        F.generationOutput.replace(F.generationSummary, ""),
        F.generationOutput + "[error] (Test / test) Tests unsuccessful\n",
        F.generationOutput + "[error] (Compile / compileIncremental) Compilation failed\n",
        F.generationOutput + "[error] (publishLocal) Publication failed\n",
        F.generationOutput + "[info] published artifact\n", F.generationOutput + "[success] complete\n",
        F.generationOutput + "[error] unexpected later failure\n")
      val terminations = Vector(ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.TerminationUnconfirmed,
        ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.Exited(0),
        ProcessExecutionTermination.Exited(2))
      val captured = WorkflowSbtCapture.decode(F.process(F.generationOutput)).toOption.get
      val invalid = outputs.map(text => failed.copy(process = Some(F.process(text)))) ++
        terminations.map(termination => failed.copy(process = Some(F.process(F.generationOutput, termination)))) ++ Vector(
          failed.copy(status = "uncertain"), failed.copy(status = "published"), failed.copy(process = None),
          failed.copy(process = Some(F.process(F.generationOutput, truncated = true))),
          failed.copy(process = Some(WorkflowSbtCapture.encode(captured.copy(
            stderr = ProcessExecutionCapture(Vector(0xc3.toByte), 1L, false))))),
          failed.copy(process = Some(F.process(F.generationOutput, identity = "other-program"))),
          failed.copy(request = failed.request.copy(participant = "other-participant")),
          failed.copy(request = failed.request.copy(input = failed.request.input.copy(ownerSessionId = Some("other-session")))),
          failed.copy(request = failed.request.copy(source = F.source.copy(tree = "e" * 40))),
          failed.copy(request = failed.request.copy(input = failed.request.input.copy(explicit = true))))
      val targets = Vector(F.target.copy(identity = "other-target"), F.target.copy(component = "other-component"),
        F.target.copy(workspace = "other-workspace"), F.target.copy(dependency = Some("dependency")), F.target.copy(beforeChecks = false))
      When("the classifier requires the complete original request source participant target and fixed program")
      val classifications = invalid.map(A.generationFailure(_, F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target))) ++
        targets.map(A.generationFailure(failed, _, "primary-session", "primary-participant", F.source, F.processPolicy(F.target))) ++
        Vector(A.generationFailure(failed, F.target, "primary-session", "primary-participant", F.source,
          F.processPolicy(F.target).replace("publish-local", "test")))
      Then("none grants a source repair or publication retry")
      classifications shouldBe Vector.fill(classifications.size)(false)
      A.failureValid(F.generationFailure.copy(candidate = F.candidate.copy(sourceRevision = "9"))) shouldBe false
    }

    "reserve one ordinary generation repair with truthful unexecuted tests and immutable source provenance" in {
      Given("one original Validation controller and the exact generation failure preceding every check")
      val wire = A.codec.encode(F.generationFact)
      val before = F.generationFailure.asJson
      When("recollection reconstructs the same repair and issues its actual publication context")
      val restored = Vector.fill(3)(A.codec.decode(wire).toOption.get).map(fact => take(A.stateC(F.controller, fact.failure)))
      val context = take(A.workContextC(F.generationFact, F.reuse.repair, F.generationRepair, "RepairStep")).get
      val request = F.request.copy(repairDirective = Some(F.generationFact.directive), publicationFailure = Some(context))
      Then("one reservation carries generation diagnostics without a test PASS or an accepted implementation replay")
      restored.map(state => (state.generation, state.repairs, state.ordinaryRepairs)) shouldBe Vector.fill(3)((2L, 1, 1))
      F.controller.repairs shouldBe 0
      F.generationFact.directive.reason should include("generation failure")
      F.generationFact.directive.findings.head should startWith("PRIMARY-PUBLICATION-GENERATION:")
      context.testsExecuted shouldBe false
      context.publication shouldBe F.generationFailure.failed
      context.changedPaths shouldBe F.candidate.changedPaths
      W.inputCodec.decode(W.inputCodec.encode(request)) shouldBe Right(request)
      request.checkFailure shouldBe None
      request.review shouldBe None
      restored.foreach { state => state.checksReceipt shouldBe None; state.reviewEvidence shouldBe None }
      F.generationFailure.asJson shouldBe before
    }
  }

  "Retained failed checks after dependency source adoption" should {
    "recognize the admitted v7 source failure without inventing independent review or successor success" in {
      Given("pure v7 source-capture data and a terminated failed selected primary check, with no review receipt")
      import PrimaryPublicationContinuationSpecificationFixture as P
      val failure = F.sourceCheckFailure(P.capture)
      val before = failure.asJson
      When("the repair provenance predicate matches the authenticated original source direction")
      val matched = GoalPhaseNativeWorkContinuation.sourceCheckFailureMatches(P.sourceDirection, failure)
      Then("the exact admitted failure is usable as context and all original failed facts remain intact")
      W.admitCheckFailureContextC(failure).isSuccess shouldBe true
      matched shouldBe true
      failure.validation.dependency shouldBe None
      failure.validation.dependencySource shouldBe Some(P.capture)
      failure.validation.outcome shouldBe Some("repair-required")
      failure.asJson shouldBe before

      Given("crossed direction, Session, Plan, primary base, Step, malformed capture and passed-result claims")
      val crossedcaptures = Vector(P.capture.copy(sessionId = "other-session"),
        P.capture.copy(plan = ContextReference("other-plan", "1")),
        P.capture.copy(primaryBase = P.capture.primaryBase.copy(tree = "a" * 40)),
        P.capture.copy(step = "other-step"), P.capture.copy(schemaVersion = "invalid-capture"))
      val crossed = crossedcaptures.map(capture => failure.copy(validation = failure.validation.copy(dependencySource = Some(capture)))) ++
        Vector(failure.copy(validation = failure.validation.copy(step = "other-step")),
          failure.copy(validation = failure.validation.copy(outcome = Some("passed"))))
      val otherdirection = P.sourceDirection.copy(directive = ContextReference("other-source-human", "1"))
      When("each claim is matched against the same authenticated direction and genuine-failure gate")
      val rejected = crossed.map(GoalPhaseNativeWorkContinuation.sourceCheckFailureMatches(P.sourceDirection, _))
      val wrongdirection = GoalPhaseNativeWorkContinuation.sourceCheckFailureMatches(otherdirection, failure)
      Then("none can supply a repair context for that source direction")
      rejected shouldBe Vector.fill(crossed.size)(false)
      wrongdirection shouldBe false
    }

    "preserve the legacy admitted dependency capture and its exact predecessor match" in {
      Given("the preexisting legacy capture with its exact Session, Plan, primary base and admitted failed check")
      import PrimaryPublicationContinuationSpecificationFixture as P
      import DependencyValidationSpecificationFixture as D
      val validation = F.primaryValidation(false)
      val failure = take(W.checkFailureContextC(Vector("screen.scala"), "SPECIFICATION-OWNED legacy failed diff",
        validation.copy(selected = validation.selected ++ D.binding.checks.map(_.copy(dependency = Some(D.binding.identity))),
          dependency = Some(D.capture))))
      val crossed = P.sourceDirection.copy(target = P.sourceTarget.copy(predecessor = D.admission.copy(approvals = Vector.empty)))
      When("the unchanged legacy branch compares its exact admission with the authenticated direction")
      val matched = GoalPhaseNativeWorkContinuation.sourceCheckFailureMatches(P.sourceDirection, failure)
      val refused = GoalPhaseNativeWorkContinuation.sourceCheckFailureMatches(crossed, failure)
      Then("only the original predecessor matches and no source capture is fabricated")
      matched shouldBe true
      refused shouldBe false
      failure.validation.dependencySource shouldBe None
      failure.validation.dependency shouldBe Some(D.capture)
    }

    "retain a later approved source-scope capture under its original direction" in {
      Given("pure original source data and an approved additional path in a later scope capture")
      import GoalPhaseDependencySourceRevision as R
      import DependencySourceRevisionSpecificationFixture as D
      import DependencyValidationSpecificationFixture as V
      val path = "src/main/scala/Additional.scala"
      val approval = V.approvals.head.copy(proposal = V.approvals.head.proposal.copy(identity = "additional-source-proposal",
        paths = Vector(path), acceptance = Vector("Qualify the additional source through the existing checks")),
        decision = ContextReference("additional-actual-decision-fixture", "1"))
      val binding = D.binding.copy(revision = D.binding.revision + 1, paths = D.binding.paths :+ path)
      val consumption = D.proposal.consumption.asInstanceOf[R.LegacyConsumption].copy(
        expectedModes = D.proposal.consumption.expectedModes :+ R.ExpectedMode(path, "100644"))
      val scope = R.ScopeSuccessor(R.scopeSchema, D.direction.directive, binding, Vector(approval), consumption,
        D.proposal.coverage :+ R.Coverage(approval.decision, approval.proposal.acceptance, binding.checks.map(_.identity)))
      val guard = D.before.copy(selectedFiles = D.before.selectedFiles :+
        R.fileIdentity(path, Some("object Additional\n".getBytes.toVector)))
      val capture = D.capture.copy(schemaVersion = R.scopeCaptureSchema, generation = 3, sourceRevision = "30",
        preStageGuard = guard, postStageGuard = guard, scope = Some(scope))
      val failure = F.sourceCheckFailure(capture)
      When("the failed-check predicate checks the later capture against its retained original direction")
      val matched = GoalPhaseNativeWorkContinuation.sourceCheckFailureMatches(D.direction, failure)
      Then("the approved scope stays intact without granting validation to a replacement source")
      R.captureValid(capture) shouldBe true
      matched shouldBe true
      failure.validation.dependencySource.get.scope shouldBe Some(scope)
      failure.validation.outcome shouldBe Some("repair-required")
    }
  }

  "Publication repair with retained operating profiles" should {
    "preserve each configured profile and its concrete attribution alongside the actual failure" in {
      Given("the issued primary repair cause and independently configured standard, cost, quality and local-experiment captures")
      val concrete = GoalPhaseOperatingProfiles.Concrete("fixture-profile", "fixture-provider", "fixture-model", Map("effort" -> "fixture"))
      Vector("standard", "cost", "quality", "local-experiment").foreach { identity =>
        val profile = GoalPhaseOperatingProfiles.Capture(
          GoalPhaseOperatingProfiles.Selection(identity, "1", "explicit"), Some("CodingEngineeringDeep"),
          Some("fixture-mapping"), Some("1"), Some(concrete), Some(concrete))
        val request = F.request.copy(operatingProfile = Some(profile))
        When("the complete repair request crosses the existing semantic codec and public work view")
        val encoded = W.inputCodec.encode(request)
        val public = NativeWorkflowOperationOutput.goalWork(request)
        Then("v6 retains both fields and leaves earlier v5 profile requests readable")
        W.inputCodec.decode(encoded) shouldBe Right(request)
        public.operatingProfile shouldBe Some(GoalPhaseNativeMetadata.record(profile.asJson))
        public.publicationFailure shouldBe Some(GoalPhaseNativeMetadata.record(F.context.asJson))
        val earlier = request.copy(publicationFailure = None)
        W.inputCodec.decode(W.inputCodec.encode(earlier)) shouldBe Right(earlier)
        W.inputCodec.encode(earlier).hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v5")
        request.publicationFailure.get.testsExecuted shouldBe false
      }
    }
  }

  "Expanded retained profile admission" should {
    "preserve unmapped standard selections without inferring concrete execution" in {
      Given("the adopted Capture contract with default, explicit and configured standard intent")
      for {
        source <- Vector("default", "explicit", "config")
        reasoning <- Vector(None, Some("CodingEngineeringMechanical"), Some("ReviewCritical"))
      } {
        val profile = GoalPhaseOperatingProfiles.Capture(
          GoalPhaseOperatingProfiles.Selection("standard", "1", source), reasoning, None, None, None, None)
        val request = F.request.copy(publicationFailure = None, operatingProfile = Some(profile))
        When("the complete unmapped capture crosses the v5 owning codec")
        val encoded = W.inputCodec.encode(request)
        val decoded = W.inputCodec.decode(encoded)
        Then("the exact selection and optional class survive while concrete observations stay absent")
        decoded shouldBe Right(request)
        W.inputCodec.encode(decoded.toOption.get) shouldBe encoded
        encoded.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v5")
        decoded.toOption.get.operatingProfile.get.configured shouldBe None
        decoded.toOption.get.operatingProfile.get.selected shouldBe None
      }
    }

    "refuse malformed or lossy profile wires under the expanded contract" in {
      Given("a canonical expanded profile accompanying an actual primary publication failure")
      val profile = GoalPhaseOperatingProfiles.Capture(
        GoalPhaseOperatingProfiles.Selection("standard", "1", "explicit"),
        Some("CodingEngineeringMechanical"), None, None, None, None)
      val request = F.request.copy(operatingProfile = Some(profile))
      val wire = W.inputCodec.encode(request)
      def changed(change: Json => Json): Json = wire.mapObject(fields => fields.add("payload",
        fields("payload").get.mapObject(payload => payload.add("operatingProfile", change(payload("operatingProfile").get)))))
      def selection(change: Json => Json): Json = changed(value => value.mapObject(fields =>
        fields.add("selection", change(fields("selection").get))))
      val malformed = Vector(
        changed(_ => Json.Null),
        changed(_.mapObject(_.add("extra", Json.True))),
        changed(_.mapObject(_.add("mappingIdentity", Json.Null))),
        changed(_.mapObject(_.add("dispatch", Json.Null))),
        selection(_.mapObject(_.add("identity", "unknown-profile".asJson))),
        selection(_.mapObject(_.add("revision", "2".asJson))),
        selection(_.mapObject(_.add("source", "unknown-source".asJson))),
        selection(_.mapObject(_.add("extra", Json.True))))
      When("the owning reader validates selection and exact canonical reprojection")
      val rejected = malformed.map(W.inputCodec.decode)
      Then("expanded acceptance neither discards unknown fields nor normalizes malformed metadata")
      rejected.foreach(_.isLeft shouldBe true)
      W.inputCodec.decode(wire) shouldBe Right(request)
      W.inputCodec.encode(W.inputCodec.decode(wire).toOption.get) shouldBe wire
      request.publicationFailure shouldBe F.request.publicationFailure
    }
  }

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
    "identify a publication context attached to the wrong work kind without accepting that contradiction" in {
      Given("an otherwise valid retained primary-publication repair fixture with an implementation work kind")
      val request = F.request.copy(kind = "ImplementStep")
      val wire = W.inputCodec.encode(request)
      When("the owning reader checks the publication context against its enclosing request")
      val decoded = W.inputCodec.decode(wire)
      Then("publication provenance stays intact and its incompatible work kind remains refused")
      decoded shouldBe Left("Invalid retained check failure context [field=publicationFailure]")
      request.publicationFailure shouldBe F.request.publicationFailure
      W.inputCodec.encode(request) shouldBe wire
    }

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
    "preserve the complete process policy except one exactly approved committed read inventory" in {
      Given("valid old and successor committed source captures plus one exact read-only inventory registration")
      import GoalPhaseDependencySourceRevision as R
      val (previous, candidate) = F.policySourceSuccessor()
      val oldbinding = R.bindingOf(previous.dependencySourceCapture.get)
      val binding = R.bindingOf(candidate.dependencySourceCapture.get)
      val inventory = Json.obj("identity" -> "fixed-inventory-program".asJson, "target" -> binding.dependency.workspace.asJson,
        "operation" -> GitOperationKind.TreeEntries.wireName.asJson, "selectionId" -> R.committedInventorySelection.asJson,
        "selectedPaths" -> oldbinding.paths.asJson, "capability" -> "fixed-read-inventory".asJson)
      val policy = Json.obj("processPlan" -> Json.obj("gitPrograms" -> Vector(inventory).asJson,
        "sbtPrograms" -> Json.arr(Json.obj("identity" -> "fixed-publication".asJson, "operation" -> "publish-local".asJson,
          "environment" -> Json.obj("FIXED_SOURCE" -> binding.root.asJson))),
        "workspaces" -> Json.arr(Json.obj("identity" -> binding.dependency.workspace.asJson, "root" -> binding.root.asJson)),
        "grants" -> Json.arr(Json.obj("capabilities" -> Json.arr("fixed-read-inventory".asJson)))),
        "maximumDiffBytes" -> 8192.asJson, "validation" -> Json.obj("checks" -> Json.arr("primary-test".asJson)))
      val current = policy.mapObject(_.add("processPlan", policy.hcursor.get[Json]("processPlan").toOption.get.mapObject(
        _.add("gitPrograms", Vector(inventory.mapObject(_.add("selectedPaths", binding.paths.asJson))).asJson))))
      When("the pure comparison authenticates the current source and restores only the previous finite selectedPaths")
      val exact = A.processPolicyMatches(previous, previous, policy.noSpaces, policy.noSpaces)
      val accepted = A.processPolicyMatches(previous, candidate, policy.noSpaces, current.noSpaces)
      Then("unchanged policies and precisely approved read succession retain fixed publication authority")
      exact shouldBe true
      A.processPolicyMatches(previous, candidate, policy.noSpaces, policy.noSpaces) shouldBe true
      accepted shouldBe true
      A.currentSourceSuccessorValid(previous, candidate) shouldBe true
      R.modeOf(previous.dependencySourceCapture.get) shouldBe "committed"
      R.modeOf(candidate.dependencySourceCapture.get) shouldBe "committed"

      Given("the same complete policies with unrelated execution changes or missing and duplicate inventories")
      val plan = current.hcursor.get[Json]("processPlan").toOption.get
      val selected = plan.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.head
      val sbt = plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head
      def _program_(value: Json): Json = current.mapObject(_.add("processPlan", plan.mapObject(_.add("gitPrograms", Vector(value).asJson))))
      def _plan_(key: String, value: Json): Json = current.mapObject(_.add("processPlan", plan.mapObject(_.add(key, value))))
      val changed = Vector(_program_(selected.mapObject(_.remove("selectedPaths"))),
        _program_(selected.mapObject(_.add("selectedPaths", (binding.paths :+ "docs/unapproved.md").asJson))),
        _program_(selected.mapObject(_.add("selectedPaths", Json.arr(1.asJson)))),
        _program_(selected.mapObject(_.add("operation", "stage-full-state".asJson))),
        _program_(selected.mapObject(_.add("capability", "other-capability".asJson))),
        _program_(selected.mapObject(_.add("target", "other-workspace".asJson))),
        _program_(selected.mapObject(_.add("identity", "other-program".asJson))),
        _plan_("gitPrograms", Json.arr()), _plan_("gitPrograms", Vector(selected, selected).asJson),
        _plan_("sbtPrograms", Vector(sbt.mapObject(_.add("identity", "other-publication".asJson))).asJson),
        _plan_("sbtPrograms", Vector(sbt.mapObject(_.add("operation", "test".asJson))).asJson),
        _plan_("sbtPrograms", Vector(sbt.mapObject(_.add("environment", Json.obj("FIXED_SOURCE" -> "/other/root".asJson)))).asJson),
        _plan_("workspaces", Json.arr()), _plan_("grants", Json.arr()),
        current.mapObject(_.add("validation", Json.obj("checks" -> Json.arr()))),
        current.mapObject(_.add("maximumDiffBytes", 8193.asJson)), current.mapObject(_.add("extra", Json.True)))
      val oldplan = policy.hcursor.get[Json]("processPlan").toOption.get
      val oldinvalid = Vector(policy.mapObject(_.add("processPlan", oldplan.mapObject(_.add("gitPrograms", Json.arr())))),
        policy.mapObject(_.add("processPlan", oldplan.mapObject(_.add("gitPrograms", Vector(inventory, inventory).asJson)))))
      When("every extra policy delta and unauthenticated source relation is compared")
      val rejected = changed.map(value => A.processPolicyMatches(previous, candidate, policy.noSpaces, value.noSpaces))
      val invalidold = oldinvalid.map(value => A.processPolicyMatches(previous, candidate, value.noSpaces, current.noSpaces))
      Then("no execution, grant, validation, destination or arbitrary inventory change is treated as source read succession")
      rejected shouldBe Vector.fill(changed.size)(false)
      invalidold shouldBe Vector(false, false)
      A.processPolicyMatches(previous, previous, policy.noSpaces, current.noSpaces) shouldBe false
      A.processPolicyMatches(previous, candidate.copy(dependencySourceScope = None), policy.noSpaces, current.noSpaces) shouldBe false
      A.processPolicyMatches(previous, candidate.copy(sourceRevision = "51"), policy.noSpaces, current.noSpaces) shouldBe false
      A.processPolicyMatches(previous, candidate, "{", current.noSpaces) shouldBe false
      val wrong = candidate.dependencySourceScope.get
      A.processPolicyMatches(previous, candidate.copy(dependencySourceDirection = Some(candidate.dependencySourceDirection.get.copy(
        directive = ContextReference("crossed-source", "1")))), policy.noSpaces, current.noSpaces) shouldBe false
      A.processPolicyMatches(previous, candidate.copy(dependencySourceScope = Some(wrong.copy(binding = wrong.binding.copy(root = "/other/root")))),
        policy.noSpaces, current.noSpaces) shouldBe false
    }

    "capture an authenticated committed source successor while retaining primary failure provenance" in {
      Given("the exact saved primary failure and an independently adopted committed source with approved additional scope")
      import GoalPhaseDependencySourceRevision as R
      val (_, scope, capture, candidate) = F.sourceSuccessor()
      val saved = A.Saved(F.nativeRecord(37), F.narrowFact)
      val from = F.nativeRecord(60)
      val progress = candidate.copy(candidate = None, changedPaths = Vector.empty, diff = None,
        dependencySourceGuard = None, dependencySourceCapture = None)
      When("candidate admission distinguishes current observation progress from historical capture reuse")
      val admitted = Vector(progress, progress.copy(dependencySourceGuard = Some(capture.preStageGuard)), candidate).map(value =>
        A.reusedCandidateC(saved, value, F.narrowRepair, from).isSuccess)
      val decoded = C.codec.decode(C.codec.encode(candidate))
      Then("v4 retains the saved failure reference and current generation without copying the old staging facts")
      admitted shouldBe Vector(true, true, true)
      decoded shouldBe Right(candidate)
      C.codec.encode(candidate).hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-candidate.v4")
      C.codec.decode(C.codec.encode(progress)) shouldBe Right(progress)
      R.captureValid(capture) shouldBe true
      R.modeOf(capture) shouldBe "committed"
      candidate.primaryPublicationRepair shouldBe Some(F.narrowReuse)
      candidate.dependencySourceCapture.get.sourceRevision shouldBe candidate.sourceRevision
      candidate.dependencySourceCapture.get.generation shouldBe F.narrowRepair.generation
      candidate.dependencySourceStages shouldBe empty
      R.captureBaseValid(capture.direction, capture.generation, capture.preStageGuard, capture.scope) shouldBe true
      R.captureBaseValid(capture.direction, capture.generation, capture.preStageGuard.copy(selectedFiles =
        capture.preStageGuard.selectedFiles.updated(0, R.fileIdentity(capture.preStageGuard.selectedFiles.head.path,
          Some("drifted current source\n".getBytes.toVector)))), capture.scope) shouldBe false
      candidate.dependencyAdmission shouldBe None
      candidate.dependency shouldBe None
      scope.binding.paths shouldBe (F.narrowFailure.candidate.dependencySourceCapture.get.direction.target.binding.paths :+
        "docs/current-integration.md")
      A.sameDependencyCapture(candidate, F.narrowFailure.candidate) shouldBe false
      A.sameDependencyCapture(F.narrowCandidate, F.narrowFailure.candidate) shouldBe true
      A.reusedCandidateC(saved, F.narrowCandidate, F.narrowRepair, F.nativeRecord(40)).isSuccess shouldBe true
    }

    "refuse crossed stale or unapproved source successor provenance without historical fallback" in {
      Given("a valid source successor and the saved failure's actual previous capture")
      import GoalPhaseDependencySourceRevision as R
      val (next, scope, _, _) = F.sourceSuccessor()
      val previous = F.narrowFailure.candidate.dependencySourceCapture.get
      val target = next.target
      val crossed = Vector(next.copy(directive = previous.direction.directive),
        next.copy(target = target.copy(sessionId = "crossed-session")),
        next.copy(target = target.copy(plan = target.plan.copy(identity = "crossed-plan"))),
        next.copy(target = target.copy(primaryBase = target.primaryBase.copy(tree = "f" * 40))),
        next.copy(target = target.copy(step = "crossed-step")),
        next.copy(target = target.copy(sourceSuccession = Some(target.sourceSuccession.get.copy(previousDirection = ContextReference("foreign", "1"))))),
        next.copy(target = target.copy(sourceRevision = previous.sourceRevision)),
        next.copy(target = target.copy(sourceSuccession = None)))
      When("the pure relation checks each actual direction and exact approval inventory")
      val rejected = crossed.map(value => A.sourceSuccessorValid(previous, Some(previous.direction),
        Some(scope.copy(consumptionDirection = Some(value)))))
      val scopes = Vector(scope.copy(approvals = Vector.empty), scope.copy(binding = scope.binding.copy(paths = scope.binding.paths.reverse)),
        scope.copy(consumptionDirection = None), scope.copy(predecessor = ContextReference("foreign", "1")))
      Then("all substitutions refuse while the unchanged original direction remains required")
      rejected shouldBe Vector.fill(crossed.size)(false)
      scopes.map(value => A.sourceSuccessorValid(previous, Some(previous.direction), Some(value))) shouldBe Vector.fill(scopes.size)(false)
      A.sourceSuccessorValid(previous, Some(next), Some(scope)) shouldBe false
      A.sourceSuccessorValid(previous, Some(previous.direction.copy(directive = ContextReference("foreign", "1"))), Some(scope)) shouldBe false
      A.sourceSuccessorValid(previous, Some(previous.direction), None) shouldBe false
    }

    "require the saved native repair prefix and reject old and new capture mixing" in {
      Given("a current-source candidate and the exact saved primary repair fact")
      val (_, _, capture, candidate) = F.sourceSuccessor()
      val saved = A.Saved(F.nativeRecord(37), F.narrowFact)
      val from = F.nativeRecord(60)
      val previous = F.narrowFailure.candidate
      val mixed = Vector(candidate.copy(dependencySourceCapture = previous.dependencySourceCapture),
        candidate.copy(dependencySourceStages = previous.dependencySourceStages),
        candidate.copy(dependencySourceGuard = previous.dependencySourceGuard),
        candidate.copy(dependencySourceCapture = Some(capture.copy(generation = capture.generation + 1))),
        candidate.copy(primaryPublicationRepair = Some(F.narrowReuse.copy(repair = ContextReference("other-session", "37")))),
        candidate.copy(primaryPublicationRepair = Some(F.narrowReuse.copy(sourceRevision = "28"))),
        candidate.copy(sourceRevision = "61"))
      When("the retained admission checks actual history, reuse reference, generation and source revision")
      val rejected = mixed.map(A.reusedCandidateC(saved, _, F.narrowRepair, from).isSuccess)
      val missing = A.reusedCandidateC(saved, candidate, F.narrowRepair, from.copy(history = from.history.drop(1)))
      Then("no stale capture or unauthenticated repair lineage is accepted")
      rejected shouldBe Vector.fill(mixed.size)(false)
      missing.isSuccess shouldBe false
      A.reusedCandidateC(saved, candidate, F.narrowRepair,
        from.copy(identity = InstanceIdentity("crossed-session"))).isSuccess shouldBe false
      A.reusedCandidateC(saved, candidate, F.repair, from).isSuccess shouldBe false
    }

    "require the actual effective previous direction for a later source successor" in {
      Given("a previously captured source successor and another direction retaining its approved inventory prefix")
      import GoalPhaseDependencySourceRevision as R
      val (previous, scope, capture, _) = F.sourceSuccessor()
      val binding = scope.binding.copy(revision = 4)
      val target = previous.target.copy(sourceRevision = "70", pendingRevision = "71", generation = 3, binding = binding,
        proposal = previous.target.proposal.copy(binding = ContextReference(binding.identity, "4")),
        sourceSuccession = Some(previous.target.sourceSuccession.get.copy(previousDirection = previous.directive)))
      val next = previous.copy(target = target, directive = ContextReference("later-current-source-human", "1"))
      val current = scope.copy(binding = binding, consumptionDirection = Some(next))
      val wrongprevious = current.copy(consumptionDirection = Some(next.copy(target = target.copy(sourceSuccession =
        Some(target.sourceSuccession.get.copy(previousDirection = capture.direction.directive))))))
      val stale = current.copy(consumptionDirection = Some(next.copy(target = target.copy(sourceRevision = "59"))))
      When("the relation authenticates the retained effective source before admitting the next observation")
      val admitted = A.sourceSuccessorValid(capture, Some(capture.direction), Some(current))
      val rejected = Vector(wrongprevious, stale).map(value => A.sourceSuccessorValid(capture, Some(capture.direction), Some(value)))
      Then("only the actual effective predecessor can advance and the source frontier must follow its capture")
      admitted shouldBe true
      rejected shouldBe Vector(false, false)
      R.sourcePreviousValid(capture.direction, next.target, Some(previous)) shouldBe true
      A.sourceSuccessorValid(capture, Some(capture.direction), Some(current.copy(approvals = Vector.empty))) shouldBe false
    }

    "authenticate contiguous current observation records independently of primary failure provenance" in {
      Given("fresh committed-source inspection, observation intent and result records in one current candidate generation")
      import GoalPhaseDependencySourceRevision as R
      val (_, _, capture, candidate) = F.sourceSuccessor()
      val progress = candidate.copy(candidate = None, changedPaths = Vector.empty, diff = None, dependencySourceCapture = None)
      def _append_(from: InstanceRecord, key: String): InstanceRecord = take(from.appendC(from.revision,
        GoalPhaseNativeRun.history(from, "phase.InspectCandidate", "ordered-candidate:" + key)))
      val inspected = C.Saved(_append_(F.nativeRecord(60), "dependency-source-inspection"), progress)
      val intent = C.Saved(_append_(inspected.record, "dependency-source-observation-intent"), progress)
      val result = C.Saved(_append_(intent.record, "dependency-source-observation-result"), progress.copy(dependencySourceCapture = Some(capture)))
      val complete = C.Saved(_append_(result.record, "candidate"), candidate)
      val facts = Map("dependency-source-inspection" -> inspected, "dependency-source-observation-intent" -> intent,
        "dependency-source-observation-result" -> result)
      When("retained source authentication reads actual adjacent records and refuses missing or rewritten lineage")
      val admitted = R.retainedCaptureC(complete, key => Consequence.success(facts.get(key)))
      val absent = R.retainedCaptureC(complete, key => Consequence.success((facts - "dependency-source-observation-intent").get(key)))
      val crossed = R.retainedCaptureC(complete, key => Consequence.success(facts.updated("dependency-source-observation-result",
        result.copy(record = result.record.copy(history = result.record.history.drop(1)))).get(key)))
      Then("only the actual current receipt chain completes the candidate")
      admitted.isSuccess shouldBe true
      absent.isSuccess shouldBe false
      crossed.isSuccess shouldBe false
      candidate.primaryPublicationRepair shouldBe Some(F.narrowReuse)
      F.narrowFailure.candidate.dependencySourceCapture shouldBe Some(PrimaryPublicationContinuationSpecificationFixture.capture)
    }

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


/** Independently selectable primary publication repair codec regression.
  * Introduced and updated Nov. 6, 2026 by ASAMI, Tomoharu.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePrerequisitePrimaryRepairCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PrimaryPublicationRepairSpecificationFixture as F

  "E14 primary publication generation repair" should afterWord(
    "in spec:goal-phase-prerequisite-publication-recovery, example:E14, rules:RGEN01, phase:6.11") {
    "retain structural v2 facts and v5 targets through canonical collection without another repair charge" in {
      Given("pure complete OPERATION and REQUIRED-OPERATION captures at the same original Step frontier")
      Vector(F.structuralMarker, F.requiredStructuralMarker).foreach { marker =>
        val failure = F.generationFailure.copy(failed = F.generationFailure.failed.copy(process = Some(F.process(F.structuralOutput(marker)))))
        val before = failure.asJson
        When("the same failure is derived twice and recollected through its canonical v2 fact")
        val states = Vector.fill(2)(take(A.stateC(F.controller, failure)))
        val fact = A.Fact(A.generationSchema, failure, A.directive(failure), F.sorted(B.controller(states.head)))
        val wire = A.codec.encode(fact)
        val restored = A.codec.decode(wire).toOption.get
        val target = F.originalTarget(failure)
        Then("one ordinary same-Step reservation and the unchanged failure survive the existing v2/v5 codecs")
        states.map(s => (s.generation, s.repairs, s.ordinaryRepairs)) shouldBe Vector.fill(2)((2L, 1, 1))
        val reconstructed = take(A.stateC(F.controller, restored.failure))
        F.sorted(B.controller(reconstructed)) shouldBe F.sorted(B.controller(states.head))
        restored shouldBe fact
        A.codec.encode(restored) shouldBe wire
        target.schemaVersion shouldBe A.generationTargetSchema
        target.asJson.as[A.Target] shouldBe Right(target)
        A.codec.decode(wire.mapObject(_.add("schemaVersion", A.schema.asJson))).isLeft shouldBe true
        target.copy(schemaVersion = A.targetSchema).asJson.as[A.Target].isLeft shouldBe true
        failure.asJson shouldBe before
        states.head.plan shouldBe F.plan
        states.head.base shouldBe F.base
        states.head.completed shouldBe F.controller.completed
        states.head.stage shouldBe O.Stage.Repair
        states.head.checksReceipt shouldBe None
      }
    }

    "roundtrip generation v2 and v5 while preserving legacy Compile v1 and v3 bytes" in {
      Given("separate pure generation and Compile facts with exact original public targets")
      val target = F.originalTarget(F.generationFailure)
      val compiletarget = F.originalTarget(F.failure)
      val wire = A.codec.encode(F.generationFact)
      val compilewire = Json.obj("schemaVersion" -> "sm-workflow.goal-phase-prerequisite-primary-repair.v1".asJson,
        "payload" -> Json.obj("schemaVersion" -> "sm-workflow.goal-phase-prerequisite-primary-repair.v1".asJson,
          "failure" -> F.failure.asJson, "directive" -> O.RepairDirective(
            "Repair the actual primary publication Compile failure within the selected Step; tests were not executed",
            Vector("PRIMARY-PUBLICATION-COMPILE: " + F.target.identity) ++ F.output.linesIterator
              .filter(_.startsWith("[error]")).toVector).asJson,
          "successorController" -> F.sorted(B.controller(F.repair)).asJson))
      val oldtarget = Json.obj("schemaVersion" -> "sm-workflow.goal-phase-prerequisite-publication-recovery.v3".asJson,
        "componentInstance" -> F.failure.componentInstance.asJson, "participant" -> F.failure.participant.asJson,
        "sessionId" -> F.failure.sessionId.asJson, "frontierRevision" -> F.failure.frontierRevision.asJson,
        "controller" -> F.failure.controller.asJson, "generation" -> F.failure.generation.asJson,
        "candidateRecordRevision" -> F.failure.candidateRecordRevision.asJson, "candidate" -> F.candidate.asJson,
        "failure" -> F.failure.asJson, "scope" -> A.scope(F.failure).asJson, "missingAuthority" -> "primary-source-repair".asJson)
        .deepDropNullValues // Historical public Target encoding recursively omits optional nulls, including inside Scope.
      val direction = A.Direction(target, "RepairPrimarySourceAndContinuePublication", "SPECIFICATION-OWNED generation correction",
        ContextReference("generation-human", "1"))
      val binding = take(A.bindingC(target.componentInstance, target, direction.choice, direction.rationale, target.participant, direction.reference))
      val description = A.description(target)
      val reference = org.simplemodeling.textus.smworkflow.value.WorkflowArtifactReference(direction.reference.identity, direction.reference.revision)
      val input = org.simplemodeling.textus.smworkflow.value.GoalPhasePrerequisitePublicationRecoveryInput(target.sessionId,
        description.target, direction.choice, direction.rationale, reference, None)
      When("the closed codecs recognizers input binding and passive Human preparation consume each version")
      val prepared = org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(
        description, input, reference, org.simplemodeling.textus.smworkflow.value.WorkflowExecutionEvidence(
          Vector(reference), Some(target.participant), None, None), target.participant, "specification-host", java.time.Instant.now())
      Then("generation uses only the new versions and old Compile serialization remains canonical")
      wire.hcursor.get[String]("schemaVersion") shouldBe Right(A.generationSchema)
      A.codec.decode(wire) shouldBe Right(F.generationFact)
      A.codec.encode(A.codec.decode(wire).toOption.get) shouldBe wire
      A.codec.decode(wire.mapObject(_.add("schemaVersion", A.schema.asJson))).isLeft shouldBe true
      F.generationFact.copy(schemaVersion = A.schema).asJson.as[A.Fact].isLeft shouldBe true
      target.asJson.as[A.Target] shouldBe Right(target)
      target.schemaVersion shouldBe A.generationTargetSchema
      target.copy(schemaVersion = A.targetSchema).asJson.as[A.Target].isLeft shouldBe true
      F.fact.copy(schemaVersion = A.generationSchema).asJson.as[A.Fact].isLeft shouldBe true
      compiletarget.copy(schemaVersion = A.generationTargetSchema).asJson.as[A.Target].isLeft shouldBe true
      A.codec.encode(F.fact).noSpaces shouldBe compilewire.noSpaces
      A.codec.decode(compilewire) shouldBe Right(F.fact)
      compiletarget.schemaVersion shouldBe A.targetSchema
      compiletarget.asJson.noSpaces shouldBe oldtarget.noSpaces
      oldtarget.as[A.Target].map(_.asJson.noSpaces) shouldBe Right(oldtarget.noSpaces)
      F.fact.directive.reason shouldBe "Repair the actual primary publication Compile failure within the selected Step; tests were not executed"
      A.directionValid(direction) shouldBe true
      take(A.isTargetC(description.target)) shouldBe true
      B.selectBinding(binding) shouldBe Right(binding)
      B.recoveryInputC(input).isSuccess shouldBe true
      prepared.isSuccess shouldBe true
    }
  }
}

/** Independently selectable regressions for the closed source-diagnostic families.
  * error. Captures and references here are pure specification data.
  * Introduced Nov. 6, 2026; updated Nov. 8, 2026 by ASAMI, Tomoharu.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePrerequisitePrimaryGenerationSyntaxSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PrimaryPublicationRepairSpecificationFixture as F

  private val _logger_messages = Vector(
    "SLF4J(W): Class path contains multiple SLF4J providers.",
    "SLF4J(W): Found provider [ch.qos.logback.classic.spi.LogbackServiceProvider@59fbb34]",
    "SLF4J(W): Found provider [org.apache.logging.slf4j.SLF4JServiceProvider@1b6924cb]",
    "SLF4J(W): See https://www.slf4j.org/codes.html#multiple_bindings for an explanation.",
    "SLF4J(I): Actual provider is of type [ch.qos.logback.classic.spi.LogbackServiceProvider@59fbb34]")

  "E16 primary generation syntax recovery" should afterWord(
    "in spec:goal-phase-prerequisite-publication-recovery, example:E16, rules:RGEN01, phase:6.11") {
    "admit complete structural operation-binding envelopes in either retained stream" in {
      Given("the exact OPERATION diagnostic and a different nonempty REQUIRED-OPERATION identity with neutral delegate scaffolding")
      val captures = Vector(F.structuralMarker, F.requiredStructuralMarker).flatMap { marker =>
        val text = F.structuralOutput(marker)
        val lines = text.linesIterator.toVector
        val split = lines.size / 2
        Vector(F.process(text), F.streamProcess("", text),
          F.streamProcess("[info] delegate started\n", text),
          F.streamProcess(lines.take(split).mkString("\n") + "\n", lines.drop(split).mkString("\n") + "\n"))
      } ++ Vector(F.process(F.generationOutput), F.process(F.generationOutput.replace("[error] Error[No input]",
        "[error] Error[org.goldenport.exception.SyntaxErrorFaultException: Operation 'echo' INPUT Value 'ProbeInput' requires input-kind=COMMAND|QUERY.]")))
      When("the closed classifier consumes complete pure captures bound to the actual-shaped publication")
      val results = captures.map(raw => A.generationFailure(F.generationFailure.failed.copy(process = Some(raw)),
        F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("all finite source families qualify without treating pure specification captures as real execution evidence")
      results shouldBe Vector.fill(captures.size)(true)
    }

    "retain the observed finite logger bootstrap scaffold with every admitted source family and complete stream layout" in {
      Given("the isolated old capture's exact SLF4J multiple-provider templates on delegated warnings and repeated outer error logs")
      val inputkind = "[error] Error[org.goldenport.exception.SyntaxErrorFaultException: Operation 'echo' INPUT Value 'ProbeInput' requires input-kind=COMMAND|QUERY.]"
      val markers = Vector(F.structuralMarker, F.requiredStructuralMarker, "[error] Error[No input]", inputkind)
      val messages = _logger_messages ++ Vector("SLF4J(W): Found provider [org.example._Provider$Nested2@A0b1]",
        "SLF4J(I): Actual provider is of type [org.example.OtherProvider@F00]")
      val warnings = messages.map("[warn] [sbt-cozy/cozy] " + _).mkString("", "\n", "\n")
      val errors = messages.map("[error] " + _).mkString("", "\n", "\n")
      val captures = markers.flatMap { marker =>
        val text = warnings + F.structuralOutput(marker).replace(F.generationDiagnostic, errors + F.generationDiagnostic)
        val lines = text.linesIterator.toVector
        val split = lines.size / 2
        Vector(F.process(text), F.streamProcess("", text),
          F.streamProcess(lines.take(split).mkString("\n") + "\n", lines.drop(split).mkString("\n") + "\n"))
      }
      When("the source classifier reads those pure complete captures with unchanged publication and source guards")
      val results = captures.map(raw => A.generationFailure(F.generationFailure.failed.copy(process = Some(raw)),
        F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("only neutral logger metadata is ignored while exact error source proof remains necessary for Generation")
      results shouldBe Vector.fill(captures.size)(true)
    }

    "refuse unknown or malformed logger bootstrap output without borrowing the recognized metadata templates" in {
      Given("a complete structural envelope crossed with logger failures malformed provider identities and other delegated tasks")
      val text = F.structuralOutput()
      val provider = "SLF4J(W): Found provider [ch.qos.logback.classic.spi.LogbackServiceProvider@59fbb34]"
      val bodies = Vector("SLF4J(W): Unknown bootstrap message.", "SLF4J(E): Failed to initialize logging.",
        "SLF4J(W): Failed to instantiate provider [ch.qos.logback.classic.spi.LogbackServiceProvider@59fbb34]",
        provider.replace("ch.qos.logback.classic.spi.LogbackServiceProvider", ""),
        provider.replace("ch.qos.logback.classic.spi.LogbackServiceProvider", "Provider"),
        provider.replace("ch.qos", "ch..qos"), provider.replace("ch.qos", "ch.2qos"),
        provider.replace("ch.qos", "ch.qos-invalid"), provider.replace("ch.qos", "ch.qos\tinvalid"),
        provider.replace("59fbb34", ""), provider.replace("59fbb34", "not-hex"),
        provider.stripSuffix("]"), provider + " extra", " " + provider,
        "SLF4J(I): Actual provider is of type [org.example.Provider@]",
        "SLF4J(W): Class path contains multiple SLF4J providers. failed",
        "SLF4J(W): See https://www.slf4j.org/codes.html#other for an explanation.",
        "(otherTask) failed", "dependency resolution failed", "Error[other source failure]")
      val captures = bodies.flatMap { body =>
        val warning = "[warn] [sbt-cozy/cozy] " + body + "\n"
        val error = "[error] " + body + "\n"
        Vector(F.process(warning + text), F.streamProcess(text, warning),
          F.process(text.replace(F.generationDiagnostic, error + F.generationDiagnostic)), F.streamProcess(text, error))
      } ++ Vector(F.process(_logger_messages.map("[error] " + _).mkString("", "\n", "\n") +
        F.structuralOutput().replace(F.structuralMarker + "\n", "")))
      When("the bounded logger predicate and existing source requirements inspect both warning and error channels")
      val results = captures.map(raw => A.generationFailure(F.generationFailure.failed.copy(process = Some(raw)),
        F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("unknown logging failures malformed class or hex syntax extra text and logger-only source proof all refuse")
      results shouldBe Vector.fill(captures.size)(false)
    }

    "retain complete source-failure envelopes whose source request and cwd paths contain spaces" in {
      Given("all finite source families with ordinary spaces in the fixed delegate source request and cwd path tails")
      val inputkind = "[error] Error[org.goldenport.exception.SyntaxErrorFaultException: Operation 'echo' INPUT Value 'ProbeInput' requires input-kind=COMMAND|QUERY.]"
      val markers = Vector(F.structuralMarker, F.requiredStructuralMarker, "[error] Error[No input]", inputkind)
      val notice = F.structuralOutput().linesIterator.find(_.startsWith("[error] [cozy.generation.acceptance] ")).get
        .stripPrefix("[error] ")
      val outputs = markers.map(marker => "[info] loading project settings\n[warn] unused fixture key\n" +
        "[warn] [sbt-cozy/cozy] " + notice + "\n" +
        F.structuralOutput(marker).replace("/specification", "/specification/primary generation"))
      val captures = outputs.flatMap(text => Vector(F.process(text), F.streamProcess("", text)))
      When("the finite envelope classifier reads complete stdout and stderr captures without changing typed source guards")
      val results = captures.map(raw => A.generationFailure(F.generationFailure.failed.copy(process = Some(raw)),
        F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("spaces preserve structural No-input and INPUT-kind classification with only neutral delegated warnings")
      results shouldBe Vector.fill(captures.size)(true)
    }

    "refuse every unrecognized delegated warning body in stdout stderr or mixed source output" in {
      Given("a valid complete structural envelope beside unknown delegated messages tasks exceptions and provenance")
      val text = F.structuralOutput()
      val prefix = "[warn] [sbt-cozy/cozy] "
      val notice = text.linesIterator.find(_.startsWith("[error] [cozy.generation.acceptance] ")).get.stripPrefix("[error] ")
      val bodies = Vector("(otherTask) failed", "dependency resolution failed", "unknown delegated diagnostic",
        "java.lang.IllegalStateException: failed", "[{\"code\":\"OTHER_FAILURE\"}]",
        "[error] " + F.generationDiagnostic.stripPrefix("[error] ").trim,
        "[info] " + notice, F.structuralMarker.stripPrefix("[error] "))
      val captures = bodies.flatMap { body =>
        val warning = prefix + body + "\n"
        Vector(F.process(text + warning), F.streamProcess(text, warning), F.streamProcess(warning, text))
      } ++ Vector(F.process(text.replace(F.structuralMarker, prefix + F.structuralMarker.stripPrefix("[error] "))),
        F.streamProcess("", text.replace(F.structuralMarker, prefix + F.structuralMarker.stripPrefix("[error] "))))
      When("the closed delegated channel inspects original untrimmed bodies in both captured streams")
      val results = captures.map(raw => A.generationFailure(F.generationFailure.failed.copy(process = Some(raw)),
        F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("unknown delegated failures or tasks cannot borrow a valid error marker and warning-only source text proves no failure")
      results shouldBe Vector.fill(captures.size)(false)
    }

    "refuse false structural contexts and every mixed failure rather than borrowing a valid source marker" in {
      Given("a qualifying structural envelope and variants with crossed source diagnostics or failure scaffolding")
      val marker = F.structuralMarker
      val text = F.structuralOutput()
      val markers = Vector(marker.replace("WORKFLOW", "STATE-MACHINE"), marker.replace("OPERATION", "ACTION"),
        marker.replace("OPERATION", "required-operation"), marker.replace("SyntaxErrorFaultException", "RuntimeException"),
        marker.replace("org.goldenport.exception.", ""), marker.replace("nested structural content", "execution-binding vocabulary"),
        marker.replace("'RepositorySyncWorkflow'", "''"), marker.replace("'RepositorySyncWorkflow'", "' workflow'"),
        marker.replace("'refreshRepositoryDirectoryConfiguration'", "'entry '"),
        marker.replace("'RepositorySyncWorkflow'", "'bad\tidentity'"), marker.replace("'RepositorySyncWorkflow'", "'bad'identity'"),
        marker.stripSuffix("]"), "prefix " + marker, " " + marker, marker + " trailing")
      val other = "[error] Error[org.goldenport.exception.SyntaxErrorFaultException: unrelated source fault.]\n"
      val extraprovenance = F.generationDiagnostic.replace("}]", ",\"detail\":\"retained\"},{\"code\":\"OTHER_FAILURE\"}]")
      val outputs = markers.map(text.replace(marker, _)) ++ Vector(
        text.replace(marker, "[error] [sbt-cozy] recent logs: " + marker),
        text.replace(marker, "[info] " + marker),
        text.replace(F.generationDiagnostic, extraprovenance),
        text.replace(F.generationDiagnostic, F.generationDiagnostic.replace("OUTPUT_TAMPERED", "SOURCE_MISMATCH")),
        text.replace(F.generationSummary, other + F.generationSummary),
        text.replace(F.generationSummary, "[error] java.lang.IllegalStateException: unrelated\n" + F.generationSummary),
        text.replace(F.generationSummary, "[error] [cozy.generation.acceptance] level=error code=PairRejected\n" + F.generationSummary),
        text.replace(F.generationSummary, "[warn] [sbt-cozy/cozy] [cozy.generation.acceptance] level=error code=PairRejected\n" + F.generationSummary),
        text.replace(F.generationSummary, "[info] Error[unrelated input failure]\n" + F.generationSummary),
        text.replace(F.generationSummary, "[warn] java.lang.IllegalStateException: unrelated\n" + F.generationSummary),
        text.replace(F.generationSummary, "[warn] [{\"code\":\"OTHER_PROVENANCE_ERROR\"}]\n" + F.generationSummary),
        text.replace(F.generationSummary, "[error] unknown failure message\n" + F.generationSummary),
        text.replace(F.generationSummary, "[error] (otherTask) failed\n" + F.generationSummary),
        text.replace(F.generationSummary, F.generationSummary + F.generationSummary))
      val failed = F.generationFailure.failed
      When("the source classifier inspects all crossed complete outputs")
      val results = outputs.map(output => A.generationFailure(failed.copy(process = Some(F.process(output))),
        F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)))
      Then("unknown errors mixed provenance admission rejection task mixtures and metadata-only markers all refuse")
      results shouldBe Vector.fill(outputs.size)(false)
    }

    "retain every original process admission and publication guard for structural source failures" in {
      Given("a pure complete structural capture crossed with nonexecution incomplete streams and different publication bindings")
      val text = F.structuralOutput()
      val failed = F.generationFailure.failed.copy(process = Some(F.process(text)))
      val captured = WorkflowSbtCapture.decode(failed.process.get).toOption.get
      val terminations = Vector(ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.Cancelled,
        ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.TerminationUnconfirmed,
        ProcessExecutionTermination.Exited(0), ProcessExecutionTermination.Exited(2))
      val invalid = terminations.map(termination => failed.copy(process = Some(F.process(text, termination)))) ++ Vector(
        failed.copy(status = "uncertain"), failed.copy(status = "not-executed"), failed.copy(status = "admission-rejected"), failed.copy(process = None),
        failed.copy(process = Some(F.process(text, truncated = true))),
        failed.copy(process = Some(F.process(text, identity = "other-program"))),
        failed.copy(process = Some(WorkflowSbtCapture.encode(captured.copy(stderr = captured.stderr.copy(truncated = true))))),
        failed.copy(process = Some(WorkflowSbtCapture.encode(captured.copy(stdout = ProcessExecutionCapture(Vector(0xc3.toByte), 1L, false))))),
        failed.copy(request = failed.request.copy(participant = "other-participant")),
        failed.copy(request = failed.request.copy(input = failed.request.input.copy(ownerSessionId = Some("other-session")))),
        failed.copy(request = failed.request.copy(input = failed.request.input.copy(explicit = true))))
      val targets = Vector(F.target.copy(identity = "other-target"), F.target.copy(component = "other-component"),
        F.target.copy(workspace = "other-workspace"), F.target.copy(beforeChecks = false), F.target.copy(dependency = Some("dependency")))
      When("the existing status target Session source fixed-program and capture guards inspect these variants")
      val results = invalid.map(A.generationFailure(_, F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target))) ++
        targets.map(A.generationFailure(failed, _, "primary-session", "primary-participant", F.source, F.processPolicy(F.target))) ++ Vector(
          A.generationFailure(failed, F.target, "wrong-session", "primary-participant", F.source, F.processPolicy(F.target)),
          A.generationFailure(failed, F.target, "primary-session", "wrong-participant", F.source, F.processPolicy(F.target)),
          A.generationFailure(failed, F.target, "primary-session", "primary-participant", F.source.copy(tree = "d" * 40), F.processPolicy(F.target)),
          A.generationFailure(failed, F.target, "primary-session", "primary-participant", F.source,
            F.processPolicy(F.target).replace("publish-local", "test")))
      Then("none establishes an eligible primary generation failure or a successful check")
      results shouldBe Vector.fill(results.size)(false)
      A.compileFailure(failed, F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)) shouldBe false
    }

    "admit only the observed INPUT-kind syntax family and reserve the next same-Step source repair" in {
      Given("the exact complete typed cozyGenerate INPUT-kind source diagnostic following one retained missing-input repair")
      val marker = "[error] Error[org.goldenport.exception.SyntaxErrorFaultException: Operation 'echo' INPUT Value 'ProbeInput' requires input-kind=COMMAND|QUERY.]"
      val text = F.generationOutput.replace("[error] Error[No input]", marker)
      val initial = F.validation(F.plan.copy(repairLimit = 2))
      val first = F.failureAt(initial, "12")
      val original = first.copy(failed = first.failed.copy(reason = "SPECIFICATION-OWNED missing-input failure",
        process = Some(F.process(F.generationOutput))))
      val firstrepair = take(A.stateC(initial, original))
      val originalfact = A.Fact(A.generationSchema, original, A.directive(original), F.sorted(B.controller(firstrepair)))
      val originalbytes = A.codec.encode(originalfact)
      val controller = F.nextValidation(firstrepair, F.snapshot.copy(tree = "d" * 40))
      val selected = F.failureAt(controller, "18")
      val failure = selected.copy(candidate = selected.candidate.copy(primaryPublicationRepair = Some(F.reuse)),
        failed = selected.failed.copy(reason = "SPECIFICATION-OWNED INPUT-kind syntax failure", process = Some(F.process(text))))
      val failed = failure.failed
      val captured = WorkflowSbtCapture.decode(failed.process.get).toOption.get
      When("the closed generation classifier inspects that actual-shaped syntax capture and its crossed variants")
      val classified = A.generationFailure(failed, F.target, "primary-session", "primary-participant",
        failed.request.source, F.processPolicy(F.target))
      val badmarkers = Vector(marker.replace("SyntaxErrorFaultException", "RuntimeException"),
        marker.replace("org.goldenport.exception.", ""), marker.replace("Operation 'echo'", "Operation ''"),
        marker.replace("Operation 'echo'", "Operation 'echo-other'"), marker.replace("'ProbeInput'", "'Probe.Input'"),
        marker.replace("INPUT Value", "OUTPUT Value"), marker.replace("COMMAND|QUERY", "QUERY|COMMAND"),
        marker.replace("COMMAND|QUERY", "COMMAND"), marker.stripSuffix(".]"), marker + " trailing error", "prefix " + marker)
      val badtexts = badmarkers.map(value => text.replace(marker, value)) ++ Vector(
        text.replace(F.generationDiagnostic, ""), text.replace(F.generationSummary, ""),
        text + "[error] (Compile / compileIncremental) Compilation failed\n",
        text + "[error] (Compile / doc) Scaladoc generation failed\n",
        text + "[error] (Test / test) Tests unsuccessful\n", text + "[error] (publishLocal) repository failed\n",
        text + "[info] published artifact\n", text + "[success] completed\n")
      val terminations = Vector(ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.Cancelled,
        ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.TerminationUnconfirmed,
        ProcessExecutionTermination.Exited(0), ProcessExecutionTermination.Exited(2))
      val invalid = badtexts.map(value => failed.copy(process = Some(F.process(value)))) ++
        terminations.map(value => failed.copy(process = Some(F.process(text, value)))) ++ Vector(
          failed.copy(status = "uncertain"), failed.copy(status = "published"), failed.copy(process = None),
          failed.copy(process = Some(F.process(text, truncated = true))),
          failed.copy(process = Some(F.process(text, identity = "other-program"))),
          failed.copy(process = Some(WorkflowSbtCapture.encode(captured.copy(stderr = captured.stderr.copy(truncated = true))))),
          failed.copy(process = Some(WorkflowSbtCapture.encode(captured.copy(stderr = ProcessExecutionCapture(Vector(0xc3.toByte), 1L, false))))),
          failed.copy(request = failed.request.copy(participant = "other-participant")),
          failed.copy(request = failed.request.copy(input = failed.request.input.copy(ownerSessionId = Some("other-session")))),
          failed.copy(request = failed.request.copy(input = failed.request.input.copy(explicit = true))))
      val targets = Vector(F.target.copy(identity = "other-target"), F.target.copy(component = "other-component"),
        F.target.copy(workspace = "other-workspace"), F.target.copy(beforeChecks = false), F.target.copy(dependency = Some("dependency")))
      val rejected = invalid.map(A.generationFailure(_, F.target, "primary-session", "primary-participant",
        failed.request.source, F.processPolicy(F.target))) ++ targets.map(A.generationFailure(failed, _,
          "primary-session", "primary-participant", failed.request.source, F.processPolicy(F.target))) ++ Vector(
        A.generationFailure(failed, F.target, "other-session", "primary-participant", failed.request.source, F.processPolicy(F.target)),
        A.generationFailure(failed, F.target, "primary-session", "primary-participant", F.source, F.processPolicy(F.target)),
        A.generationFailure(failed, F.target, "primary-session", "primary-participant", failed.request.source,
          F.processPolicy(F.target).replace("publish-local", "test")))
      Then("only the bound terminated source family is Generation and none of the failures becomes Compile or success")
      classified shouldBe true
      rejected shouldBe Vector.fill(rejected.size)(false)
      A.compileFailure(failed, F.target, "primary-session", "primary-participant", failed.request.source, F.processPolicy(F.target)) shouldBe false
      A.failureKind(failure) shouldBe Some(A.FailureKind.Generation)
      A.failureValid(failure) shouldBe true

      When("the second failure derives its own v2 fact and ordinary same-Step repair under the original limit")
      val repair = take(A.stateC(controller, failure))
      val fact = A.Fact(A.generationSchema, failure, A.directive(failure), F.sorted(B.controller(repair)))
      val wire = A.codec.encode(fact)
      val context = take(A.workContextC(fact, ContextReference("primary-session", "19"), repair, "RepairStep")).get
      Then("two genuine source failures consume two ordinary reservations without rewriting the first failure or granting a check receipt")
      (repair.generation, repair.repairs, repair.ordinaryRepairs) shouldBe (3L, 2, 2)
      repair.plan.repairLimit shouldBe 2
      repair.plan shouldBe original.plan
      repair.base shouldBe original.base
      repair.completed shouldBe original.completed
      repair.stage shouldBe O.Stage.Repair
      repair.checksReceipt shouldBe None
      repair.reviewEvidence shouldBe None
      A.codec.decode(wire) shouldBe Right(fact)
      wire should not be originalbytes
      wire.hcursor.get[String]("schemaVersion") shouldBe Right(A.generationSchema)
      A.publicationSchema(failure) shouldBe A.generationTargetSchema
      context.publication shouldBe failed
      context.testsExecuted shouldBe false
      context.publication should not be original.failed
      A.codec.encode(originalfact) shouldBe originalbytes
      originalfact.failure.failed.process shouldBe Some(F.process(F.generationOutput))
    }
  }
}

/** Pure authored-file conformance, introduced and updated Nov. 6, 2026 by
  * ASAMI, Tomoharu. Literal fixture data and executable declarations are input. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePrimaryRepairHistoryConformanceSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  "R5-SH02/R5-SH03 authored primary repair history" should {
    "interpret both explicit authored files idempotently without changing fixture or declaration bodies" in {
      Given("the existing external goldenport rule and exactly two authored source inputs from the repository cwd")
      val inputs = Vector(
        "src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhasePrerequisitePrimaryRepairSpec.scala" ->
          "/** Primary publication repair authority, attribution and retained capture contracts.",
        "src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalPrimaryGenerationRecoveryCliSpec.scala" ->
          "/** Paired installed-only qualification of one real tiny sbt-cozy failure.")
      val rule = take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey)))
      rule.sourceRoots shouldBe Vector("src/main/scala", "src/test/scala")
      val date = java.time.LocalDate.parse("2026-11-06")
      inputs.foreach { (path, marker) =>
        val original = java.nio.file.Files.readString(java.nio.file.Path.of(path))
        def _outside_header_(text: String): (String, String) = {
          val start = text.indexOf(marker)
          start should be >= 0
          val end = text.indexOf("*/", start) + 2
          end should be > start
          (text.take(start), text.drop(end))
        }
        When("the pure interpreter performs the first canonical header normalization and repeats the same date for " + path)
        val first = take(WorkflowSourceHistoryRule.applyC(rule,
          WorkflowSourceHistoryRule.Input(path, original, date, None, None, true)))
        val second = take(WorkflowSourceHistoryRule.applyC(rule,
          WorkflowSourceHistoryRule.Input(path, first.text, date, None, None, true)))
        Then("all fixture strings and declarations remain exact outside the sole file header and the second interpretation is unchanged")
        Set("updated", "unchanged")(first.outcome) shouldBe true
        _outside_header_(first.text) shouldBe _outside_header_(original)
        second.outcome shouldBe "unchanged"
        second.text shouldBe first.text
        if (path.endsWith("NativeGoalPrimaryGenerationRecoveryCliSpec.scala")) {
          original should include("|/** @since Nov. 6, 2026")
          first.text should include("|/** @since Nov. 6, 2026")
        }
      }
    }
  }
}
