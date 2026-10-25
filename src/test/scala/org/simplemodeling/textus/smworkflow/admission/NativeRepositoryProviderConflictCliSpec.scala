package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.{WorkflowConcreteExecutionProfile, WorkflowExecutionHarness as H, WorkflowExecutionProfileParameters}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ExecutionIndependence, ExecutionPlacement}
import org.goldenport.convert.ValueReader
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Explicit ordinary Repository commands around an external semantic proposal.
  * Supplied observations check coherence and authenticate no provider provenance.
  * Runtime owns conflict materialization, selected staging and required checks.
  * @since   Oct. 17, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryProviderConflictCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, record, take}

  private val _provider = WorkflowConcreteExecutionProfile("p61-git-provider-sol-medium", "openai", "gpt-6.1-sol",
    Map("reasoning-effort" -> "medium"))
  private val _parent = WorkflowConcreteExecutionProfile("p61-parent-sol-high", "openai", "gpt-6.1-sol",
    Map("reasoning-effort" -> "high"))
  private def _property(name: String): String = sys.props.get(name).filter(_.trim.nonEmpty)
    .getOrElse(throw new AssertionError("Select explicit " + name))
  private def _read(path: Path): Json = io.circe.parser.parse(Files.readString(path, UTF_8)).fold(
    error => throw new AssertionError(error.message), identity)
  private def _decode[A: ValueReader](value: Json): A = take(summon[ValueReader[A]].readC(record(value)))
  private def _load[A: ValueReader](root: Path, name: String): A = _decode[A](_read(root.resolve(name)))
  private def _save(root: Path, name: String, text: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _executions(root: Path): Vector[Long] = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  private def _mapping: Json = Json.obj(
    "schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
    "identity" -> Json.fromString("p61-git-provider-mapping"), "revision" -> Json.fromString("1"),
    "profiles" -> Json.arr(NativeCallerExecutionFixture.profile(_provider), NativeCallerExecutionFixture.profile(_parent)),
    "classes" -> Json.fromValues(Vector("CodingProgrammingStandard", "ReviewStandard").map(name =>
      Json.obj("reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(_provider.identity)))),
    "fallbacks" -> Json.arr())
  private def _origin(context: String): Json = Json.obj(
    "schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
    "current" -> NativeCallerExecutionFixture.executor(_parent, context),
    "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.True))
  private def _classifications: Json = {
    import GoalPhaseWorkClassification.*
    val coding = Classification(Kind.CODING, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL)
    val review = Classification(Kind.REVIEW, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL)
    RepositoryExecutionFixture.classificationJson(RepositorySyncNativeExecution.Classifications(review, coding, Some(coding)))
  }
  private def _advance(scenario: F.Scenario, session: String): V.RepositorySyncAdvanceResult =
    scenario.repositoryCall(RepositorySyncManagedFetch.advanceOperation.operation,
      Vector("--sessionId", session)).business[V.RepositorySyncAdvanceResult]
  private def _ordinary(scenario: F.Scenario): Unit = {
    scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
    scenario.invocations.map(_.pid) should not contain ProcessHandle.current().pid()
    scenario.invocations.foreach { invocation =>
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
  }

  "Ordinary RepositorySync genuine provider conflict commands" should afterWord(
    "in spec:normal-typed-git-workflow, example:P61-COMMAND-B5, rule:owning-workflow") {
    "prepare actual provider conflict" in {
      Given("an owned diverged value.txt, required ProductCheck and explicit requested medium provider mapping with parent origin")
      val origincontext = _property("smworkflow.spec.providerOrigin")
      val mapping = _mapping
      val origin = _origin(origincontext)
      val scenario = F.freshRepositoryConflict("provider-git-conflict", TypedSbtHostPlanFixture.normalExecutable,
        mapping, origin, _classifications)
      val value = Path.of(scenario.host.target.root).resolve("value.txt")
      Files.readString(value) shouldBe "local product input\n"
      val startinput = scenario.host.repository.input
      val inputuri = _save(scenario.root, "git-provider-start-input.json", json(startinput.toRecord()).noSpaces)
      When("normal prepareRepositoryStart creates the selected import for that exact public input before ordinary Start and Advance")
      val selection = scenario.prepareHostInput("prepareRepositoryStart", V.WorkflowRepositoryStartPreparationInput(
        inputuri, "p61-git-provider-selection", "participant", "ExplicitHuman", "p61-git-provider-start",
        "entity:smworkflow.cli.repository_selections").toRecord())
      scenario.selectPreparedImport(selection)
      val started = scenario.repositoryCall(RepositorySyncManagedFetch.startOperation.operation,
        Vector("--input", json(startinput.toRecord()).noSpaces)).business[V.RepositorySyncStartResult]
      val issued = _advance(scenario, started.sessionId)
      Then("the actual canonical session requests only the observed conflict and delegated coding work without running SBT")
      started.outcome shouldBe "started"
      started.sessionId.trim should not be empty
      started.message.trim should not be empty
      issued.sessionId shouldBe started.sessionId
      issued.kind shouldBe "Request"
      issued.submitOperation shouldBe Some(RepositorySyncNativeConflictContinuation.completionOperation.operation)
      issued.submitSessionId shouldBe Some(started.sessionId)
      issued.resultType shouldBe Some("RepositorySyncConflictResult")
      issued.review shouldBe None
      issued.convergence shouldBe None
      val conflict = issued.conflict.get
      conflict.conflicts.map(c => c.identity -> c.path) shouldBe Vector("conflict-1" -> "value.txt")
      val selected = conflict.conflicts.head
      selected.base.text shouldBe "initial\n"
      selected.local.text shouldBe "local product input\n"
      selected.remote.text shouldBe "remote product input\n"
      Vector(conflict.localTip, conflict.remoteTip).foreach(_.trim should not be empty)
      conflict.localTip should not be conflict.remoteTip
      val blobs = Vector(selected.base.identity, selected.local.identity, selected.remote.identity)
      blobs.foreach(_.trim should not be empty)
      blobs.distinct.size shouldBe 3
      selected.worktree should include("<<<<<<<")
      selected.worktree should include("=======")
      selected.worktree should include(">>>>>>>")
      Files.readString(value) shouldBe selected.worktree
      val context = issued.issuedWorkContext.get
      context.thinkingMode shouldBe "implementation"
      val requirement = take(H.decodeIssuedRequirementC(json(context.executionRequirement)))
      requirement.reasoningClass.get.value shouldBe "CodingProgrammingStandard"
      requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
      requirement.disposition.get.originContextIdentity shouldBe origincontext
      _executions(scenario.root) shouldBe empty

      Given("the actual pending conflict and original issued context before any semantic provider result")
      When("another ordinary Advance rereads the pending request")
      val repeated = _advance(scenario, started.sessionId)
      Then("the complete request and conflict text remain identical without SBT")
      repeated shouldBe issued
      Files.readString(value) shouldBe selected.worktree
      _executions(scenario.root) shouldBe empty

      Given("the actual issued context and saved original mapping and origin URIs")
      val mappinguri = _save(scenario.root, "git-provider-mapping.json", mapping.noSpaces)
      val originuri = _save(scenario.root, "git-provider-origin.json", origin.noSpaces)
      When("normal prepareExecution selects the actual first medium attempt for external semantic work")
      val prepared = scenario.prepareHostInput("prepareExecution",
        V.WorkflowCallerExecutionPrepareInput(context, mappinguri, originuri).toRecord()).business[V.WorkflowCallerExecutionState]
      Then("the opaque original session is pending and its passive input names the actual files without fabricating a result or observation")
      prepared.completionEvidence shouldBe None
      val session = take(H.decodeC(prepared.session))
      session.nextSelection shouldBe Some(H.Selection(1, _provider))
      prepared.nextSelection shouldBe Some(take(H.encodeSelectionC(H.Selection(1, _provider))))
      session.origin.contextIdentity shouldBe origincontext
      session.origin.profile shouldBe _parent
      session.mappingIdentity shouldBe "p61-git-provider-mapping"
      session.mappingRevision shouldBe "1"
      session.requirement shouldBe requirement
      session.workReference.handle.instanceIdentity.value shouldBe started.sessionId
      session.alternate shouldBe None
      val starturi = _save(scenario.root, "git-provider-start.json", json(started.toRecord()).noSpaces)
      val issueduri = _save(scenario.root, "git-provider-issued.json", json(issued.toRecord()).noSpaces)
      val prepareduri = _save(scenario.root, "git-provider-prepared.json", json(prepared.toRecord()).noSpaces)
      val sessionuri = _save(scenario.root, "git-provider-session.json", prepared.session)
      _save(scenario.root, "git-provider-input.json", Json.obj(
        "root" -> Json.fromString(scenario.root.toString), "repository" -> Json.fromString(value.getParent.toString),
        "value" -> Json.fromString(value.toString), "startInput" -> Json.fromString(inputuri),
        "start" -> Json.fromString(starturi), "issued" -> Json.fromString(issueduri),
        "prepared" -> Json.fromString(prepareduri), "session" -> Json.fromString(sessionuri),
        "mapping" -> Json.fromString(mappinguri), "origin" -> Json.fromString(originuri)).noSpaces)
      Files.readString(value) shouldBe selected.worktree
      Files.exists(scenario.root.resolve("git-provider-result.json")) shouldBe false
      Files.exists(scenario.root.resolve("git-provider-observation.json")) shouldBe false
      _executions(scenario.root) shouldBe empty
      _ordinary(scenario)
      println("P61_GIT_PROVIDER_ROOT=" + scenario.root)
    }

    "consume actual provider conflict" in {
      Given("one explicit prepared root, an external proposal-only result and a parent observation recorded after actual provider completion")
      val scenario = F.reopenRepositoryConflict(Path.of(_property("smworkflow.spec.gitProviderRoot")),
        TypedSbtHostPlanFixture.normalExecutable)
      val value = Path.of(scenario.host.target.root).resolve("value.txt")
      val started = _load[V.RepositorySyncStartResult](scenario.root, "git-provider-start.json")
      val issued = _load[V.RepositorySyncAdvanceResult](scenario.root, "git-provider-issued.json")
      val prepared = _load[V.WorkflowCallerExecutionState](scenario.root, "git-provider-prepared.json")
      val sessionfile = scenario.root.resolve("git-provider-session.json")
      val sessiontext = Files.readString(sessionfile, UTF_8)
      val session = take(H.decodeC(sessiontext))
      val resultpair = _read(scenario.root.resolve("git-provider-result.json"))
      resultpair.asObject.get.keys.toSet shouldBe Set("result", "reference")
      val resultjson = resultpair.hcursor.downField("result").focus.get
      resultjson.asObject.get.keys.toSet shouldBe Set("decision", "resolutions", "rationale")
      resultjson.hcursor.downField("resolutions").focus.get.asArray.get.foreach { resolution =>
        resolution.asObject.get.keys.toSet shouldBe Set("conflictIdentity", "text")
      }
      val result = _decode[V.RepositorySyncConflictResult](resultjson)
      val reference = _decode[V.WorkflowArtifactReference](resultpair.hcursor.downField("reference").focus.get)
      val observationfile = scenario.root.resolve("git-provider-observation.json")
      val observedjson = _read(observationfile)
      val observed = take(H.decodeAttemptC(observedjson))
      When("the saved public inputs and actual observation are correlated with the original pending selection and a separate ordinary Advance")
      val repeated = _advance(scenario, started.sessionId)
      Then("the original conflict remains pending and unchanged while the supplied completion belongs to that medium child context")
      started.outcome shouldBe "started"
      issued.sessionId shouldBe started.sessionId
      issued.kind shouldBe "Request"
      issued.submitSessionId shouldBe Some(started.sessionId)
      issued.submitOperation shouldBe Some(RepositorySyncNativeConflictContinuation.completionOperation.operation)
      issued.resultType shouldBe Some("RepositorySyncConflictResult")
      repeated shouldBe issued
      val conflict = issued.conflict.get
      Files.readString(value) shouldBe conflict.conflicts.head.worktree
      _executions(scenario.root) shouldBe empty
      prepared.session shouldBe sessiontext
      prepared.completionEvidence shouldBe None
      session.nextSelection shouldBe Some(H.Selection(1, _provider))
      prepared.nextSelection shouldBe Some(take(H.encodeSelectionC(H.Selection(1, _provider))))
      session.workReference.handle.instanceIdentity.value shouldBe started.sessionId
      session.workReference.runId.value shouldBe issued.issuedWorkContext.get.runId
      session.workReference.continuationId.value shouldBe issued.issuedWorkContext.get.continuationId
      session.workReference.expectedRevision.value shouldBe issued.issuedWorkContext.get.expectedRevision
      session.requirement shouldBe take(H.decodeIssuedRequirementC(json(issued.issuedWorkContext.get.executionRequirement)))
      session.mappingIdentity shouldBe "p61-git-provider-mapping"
      session.mappingRevision shouldBe "1"
      session.origin.profile shouldBe _parent
      session.alternate shouldBe None
      observed.outcome.status shouldBe H.Status.Completed
      observed.outcome.reason shouldBe H.Reason.Completed
      Some(observed.selection) shouldBe session.nextSelection
      val executor = observed.outcome.executor.get
      executor.profile shouldBe _provider
      executor.contextIdentity shouldBe reference.identity
      executor.contextIdentity should not be session.origin.contextIdentity
      reference.revision shouldBe "1"
      result.decision shouldBe "resolved"
      result.resolutions.map(_.conflictIdentity) shouldBe conflict.conflicts.map(_.identity)
      result.rationale.trim should not be empty
      observedjson.hcursor.downField("resources").focus.foreach { resources =>
        resources.asObject.get.values.foreach(_ should not be Json.Null)
      }

      Given("the actual original issued context, opaque session, completed observation and semantic child reference")
      val referenceuri = _save(scenario.root, "git-provider-reference.json", json(reference.toRecord()).noSpaces)
      When("normal recordExecution consumes only those inputs and returns actual completion evidence")
      val recorded = scenario.prepareHostInput("recordExecution", V.WorkflowCallerExecutionRecordInput(
        issued.issuedWorkContext.get, sessionfile.toUri.toASCIIString, observationfile.toUri.toASCIIString,
        Some(referenceuri)).toRecord()).business[V.WorkflowCallerExecutionState]
      Then("the selection completes without materializing the proposal or starting SBT")
      recorded.nextSelection shouldBe None
      recorded.completionEvidence should not be empty
      recorded.completionEvidence.get.references shouldBe Vector(reference)
      recorded.completionEvidence.get.workerIdentity shouldBe Some(executor.contextIdentity)
      recorded.completionEvidence.get.execution.get.profileIdentity shouldBe _provider.identity
      Files.readString(value) shouldBe conflict.conflicts.head.worktree
      _executions(scenario.root) shouldBe empty

      Given("the genuine external proposal and returned execution evidence for only the issued conflict")
      When("a separate normal named Repository Submit returns that semantic result without file or check effects")
      val accepted = scenario.repositoryCall(issued.submitOperation.get, Vector("--sessionId", issued.submitSessionId.get,
        "--result", json(result.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
        "--executionEvidence", json(recorded.completionEvidence.get.toRecord()).noSpaces)).business[V.RepositorySyncConflictSubmissionResult]
      Then("Submit accepts on the same session and leaves the original conflict text and absent SBT marker")
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "accepted"
      accepted.sessionId shouldBe started.sessionId
      accepted.message.trim should not be empty
      Files.readString(value) shouldBe conflict.conflicts.head.worktree
      _executions(scenario.root) shouldBe empty

      Given("the retained resolution proposal and required single Test policy with no review result or convergence authority")
      When("a separate ordinary Advance materializes the proposal, stages the selected path and runs structural checks and fresh Test")
      val review = _advance(scenario, started.sessionId)
      Then("the new independent merge Review contains both actual parent diffs, the selected index and completed required checks")
      review.kind shouldBe "Request"
      review.sessionId shouldBe started.sessionId
      review.submitOperation shouldBe Some(RepositorySyncNativeMergeReviewContinuation.completionOperation.operation)
      review.submitSessionId shouldBe Some(started.sessionId)
      review.resultType shouldBe Some("RepositorySyncNativeMergeReviewResult")
      review.conflict shouldBe None
      review.convergence shouldBe None
      val proposal = result.resolutions.head.text
      Files.readString(value) shouldBe proposal
      val work = review.review.get
      work.changedPaths shouldBe Vector("value.txt")
      proposal.linesIterator.filter(_.nonEmpty).foreach { line =>
        work.localDiff should include("+" + line)
        work.remoteDiff should include("+" + line)
      }
      work.indexTree.trim should not be empty
      work.localTip shouldBe conflict.localTip
      work.remoteTip shouldBe conflict.remoteTip
      work.validationMode shouldBe "required"
      work.selectedChecks shouldBe Vector("test")
      work.checks.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
      work.checks.take(2).foreach { check =>
        check.termination shouldBe "exited"
        Set(0, 2) should contain(check.exitCode)
      }
      val positive = work.checks.last
      positive.termination shouldBe "exited"
      positive.exitCode shouldBe 0
      positive.stdout should include("PRODUCT_CHECK_ENTERED " + value.getParent)
      positive.stdout should include("PRODUCT_CHECK_PASSED")
      val successfulpids = _executions(scenario.root)
      successfulpids.size shouldBe 1
      successfulpids.distinct.size shouldBe 1
      successfulpids should not contain ProcessHandle.current().pid()
      review.issuedWorkContext should not be issued.issuedWorkContext
      val reviewcontext = review.issuedWorkContext.get
      reviewcontext.thinkingMode shouldBe "review"
      val requirement = take(H.decodeIssuedRequirementC(json(reviewcontext.executionRequirement)))
      requirement.reasoningClass.get.value shouldBe "ReviewStandard"
      requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
      requirement.disposition.get.independence shouldBe ExecutionIndependence.Required
      requirement.disposition.get.originContextIdentity shouldBe session.origin.contextIdentity
      requirement.reviewRequired shouldBe true

      Given("independent merge Review is pending without supplied review evidence or completion")
      When("a final separate ordinary Advance rereads the same Review")
      val repeatedreview = _advance(scenario, started.sessionId)
      Then("the public Review and context remain identical without a second SBT and all business calls use distinct standard CLI processes")
      repeatedreview shouldBe review
      repeatedreview.issuedWorkContext shouldBe review.issuedWorkContext
      _executions(scenario.root) shouldBe successfulpids
      _ordinary(scenario)
    }
  }
}
