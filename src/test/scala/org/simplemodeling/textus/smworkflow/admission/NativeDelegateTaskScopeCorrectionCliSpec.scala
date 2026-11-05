package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.cncf.workflow.WorkflowExecutionProfileParameters
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take
import StandaloneFixExecution as S
import S.given

/** Explicit installed qualification over a tiny, isolated destination.
  * All Human selections, worker observations and semantic facts below are
  * SPECIFICATION-OWNED. They establish no actual consumer consent or delivery.
  * @since   Nov. 5, 2026
  * @version Nov. 5, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskScopeCorrectionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private lazy val _executable: Path = {
    val path = Path.of(sys.props.get("smworkflow.spec.installedCommand")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select smworkflow.spec.installedCommand or SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path) && Files.isExecutable(path),
      "Select the actual absolute installed executable")
    path
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _state(view: V.DelegateTaskView): S.State =
    GoalPhaseNativeMetadata.normalizePublicJson(parser.parse(take(view.state.get.toJsonStringC)).toOption.get)
      .toOption.get.as[S.State].toOption.get
  private def _call(scenario: F.Scenario, service: String, operation: String, parameters: Vector[String]): F.Result =
    scenario.callInstalled(_executable, service, operation, parameters, installations = None,
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))

  "Installed pending destination correction" should {
    "admit an independently selected timing subset on the same Session and reject the retired worker without executing checks or publication" in {
      Given("a fresh tiny destination with the existing concrete SPECIFICATION-OWNED DesignStandard profile")
      val parameters = BoundedWorkExecutionAdmissionFixture.parameters
      val mapping = parser.parse(parameters(WorkflowExecutionProfileParameters.declaration.name)).toOption.get
      val origin = parser.parse(parameters(GoalPhaseNativeExecution.declaration.name)).toOption.get
      val scenario = F.fresh("installed-delegate-scope-correction", bootstrapSelectedInput = false,
        mappedExecution = Some(mapping -> origin))
      sys.props.get("smworkflow.spec.componentFile").foreach { supplied =>
        val component = Path.of(supplied)
        require(component.isAbsolute && component.normalize == component && Files.isRegularFile(component),
          "Select an actual absolute published Component archive")
        val configfile = scenario.root.resolve(".textus/config.json")
        val config = parser.parse(Files.readString(configfile)).toOption.get
        Files.writeString(configfile, config.mapObject(_.add("textus.component.file", supplied.asJson)).noSpaces)
      }
      val request = V.DelegateTaskInput(scenario.host.goal.input.repositoryIdentity, "installed-condition-cycle",
        "SPECIFICATION-OWNED retain the tiny selected document and qualify delivery later",
        NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.of("behavior", "delivery"),
        NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-request", "1")), false,
        "documentation", "off", 10, "direct-instruction", Vector.empty,
        V.DelegateTaskContract("SPECIFICATION-OWNED-requester", "SPECIFICATION-OWNED-destination",
          "SPECIFICATION-OWNED-requester", "org.example.ScopeCorrectionFixture", "destination", "documentation",
          NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
          Vector("remote-push"), NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-Human", "1")),
          None, Vector.empty, "SPECIFICATION-OWNED fixture; actual consumer remains untouched"))
      val repository = Path.of(scenario.host.target.root)
      val document = Files.readString(repository.resolve("docs/phase.md"))
      val head = scenario.candidateHead
      val index = scenario.candidateIndexDiff
      val status = scenario.candidateStatus
      val indexbytes = Files.readAllBytes(repository.resolve(".git/index")).toVector
      val requestfile = scenario.root.resolve("delegate-request.json")
      Files.writeString(requestfile, _json(request))
      val startpreparation = V.WorkflowDelegateTaskStartPreparationInput(requestfile.toUri.toASCIIString,
        "initial-selection", "participant", "ExplicitHuman", "initial-key", "entity:smworkflow.cli.goal_selection")

      When("the installed public preparation reads the request and the fixture independently selects its passive import")
      val prepared = _call(scenario, "NativeWorkflowPreparationService", "prepareDelegateTaskStart",
        Vector("--input", _json(startpreparation)))
      scenario.selectPreparedImport(prepared)
      val startedresult = _call(scenario, "DelegateTaskService", "startDelegateTask", Vector("--input", _json(request),
        "--selectionId", "initial-selection", "--participant", "participant", "--idempotencyKey", "initial-key"))
      val started = startedresult.business[V.DelegateTaskView]
      def _advance_() = _call(scenario, "DelegateTaskService", "advanceDelegateTask",
        Vector("--sessionId", started.sessionId)).business[V.DelegateTaskView]
      val old = _advance_()
      val before = _state(old)
      val oldcontext = old.issuedWorkContext.get
      Then("normal Advance retains the exact initial Implement assignment")
      before.stage shouldBe "Implement"
      old.kind shouldBe "Request"
      before.input.conditions shouldBe Vector("behavior", "delivery")
      before.input.submissionConditions shouldBe None

      When("the public structured description names this still-unclaimed issue and the selected submission IDs")
      val descriptioninput = V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, oldcontext.continuationId,
        Vector.empty, Vector("behavior"))
      val describedresult = _call(scenario, "DelegateTaskService", "describeDelegateTaskScopeCorrection",
        Vector("--input", _json(descriptioninput)))
      val described = describedresult.business[V.DelegateTaskScopeCorrectionView]
      val originaltarget = take(DelegateTaskScopeCorrection.targetC(described.target))
      Then("description has no Human authority or effect and leaves the original issue pending")
      described.outcome shouldBe "decision-required"
      _advance_().issuedWorkContext shouldBe old.issuedWorkContext
      val correction = V.DelegateTaskScopeCorrectionInput(started.sessionId, oldcontext.continuationId, described.target,
        "timing-selection", "participant", "timing-key", V.WorkflowArtifactReference("SPECIFICATION-OWNED-timing-Human", "1"))
      val correctionfile = scenario.root.resolve("scope-correction.json")
      Files.writeString(correctionfile, _json(correction))
      val correctionpreparation = V.WorkflowDelegateTaskScopeCorrectionPreparationInput(correctionfile.toUri.toASCIIString,
        correction.selectionId, correction.participant, "ExplicitHuman", correction.idempotencyKey,
        "entity:smworkflow.cli.goal_selection")
      When("correction is requested before the fixture independently selects its exact Human input")
      val unselected = _call(scenario, "DelegateTaskService", "correctDelegateTaskScope", Vector("--input", _json(correction)))
      Then("passive description alone cannot supersede the assignment")
      unselected.exitCode should not be 0
      When("the normal preparation output is independently adopted as SPECIFICATION-OWNED ExplicitHuman selection")
      val correctionprepared = _call(scenario, "NativeWorkflowPreparationService", "prepareDelegateTaskScopeCorrection",
        Vector("--input", _json(correctionpreparation)))
      scenario.selectPreparedImport(correctionprepared)
      val crossed = correction.copy(target = originaltarget.copy(submissionConditions = Some(Vector("delivery"))).asJson.noSpaces)
      val refusedsubset = _call(scenario, "DelegateTaskService", "correctDelegateTaskScope", Vector("--input", _json(crossed)))
      Then("a changed subset cannot reuse that exact selection")
      refusedsubset.exitCode should not be 0
      When("the exact selected correction is admitted through the installed public operation")
      val correctedresult = _call(scenario, "DelegateTaskService", "correctDelegateTaskScope", Vector("--input", _json(correction)))
      val corrected = correctedresult.business[V.DelegateTaskScopeCorrectionView]
      val repeated = _call(scenario, "DelegateTaskService", "correctDelegateTaskScope",
        Vector("--input", _json(correction))).business[V.DelegateTaskScopeCorrectionView]
      val next = _advance_()
      val after = _state(next)
      val successor = take(DelegateTaskScopeCorrection.targetC(corrected.target))
      Then("the same Session retains the base counters and all condition identities while ordinary Advance issues a new WorkOrder")
      corrected.outcome shouldBe "corrected-awaiting-advance"
      repeated.outcome shouldBe "already-corrected"
      next.sessionId shouldBe old.sessionId
      next.issuedWorkContext.get.continuationId should not be oldcontext.continuationId
      next.resultType shouldBe old.resultType
      next.submitSessionId shouldBe old.submitSessionId
      after.input.conditions shouldBe before.input.conditions
      after.input.submissionConditions shouldBe Some(Vector("behavior"))
      after.input.copy(submissionConditions = None) shouldBe before.input
      after.copy(input = before.input, evidenceReferences = before.evidenceReferences) shouldBe before
      successor.business.baseHead shouldBe originaltarget.business.baseHead
      successor.business shouldBe originaltarget.business
      after.generation shouldBe before.generation
      after.automaticCount shouldBe before.automaticCount
      after.ordinaryCount shouldBe before.ordinaryCount
      after.repairControl shouldBe before.repairControl
      _advance_().issuedWorkContext shouldBe next.issuedWorkContext

      Given("a SPECIFICATION-OWNED submission with every original ID and delivery explicitly still false")
      val result = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED submission for later qualification", Vector.empty,
        NonEmptyVector.of(V.DelegateTaskCondition("behavior", true, "SPECIFICATION-OWNED submission"),
          V.DelegateTaskCondition("delivery", false, "Await actual runtime checks and delivery")), Vector.empty, None, None)
      val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-submission", "1")
      val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED-worker"), None,
        Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
          BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity)))
      def _submit_(context: Option[V.WorkflowIssuedWorkContext]) = _call(scenario, "DelegateTaskService",
        "submitDelegateTaskWorkResult", Vector("--sessionId", started.sessionId, "--result", _json(result),
          "--resultReference", _json(reference), "--executionEvidence", _json(evidence)) ++
          context.toVector.flatMap(value => Vector("--issuedWorkContext", _json(value))))
      When("the retired issue or an unbound worker attempts to submit against the successor")
      val stale = _submit_(Some(oldcontext))
      val unbound = _submit_(None)
      Then("both are refused even though the original condition identities are present")
      stale.exitCode should not be 0
      unbound.exitCode should not be 0
      When("the same result carries the actual current issued binding")
      val submitted = _submit_(next.issuedWorkContext)
      Then("only that successor submission is retained and no checks publication or file effects have run")
      submitted.exitCode shouldBe 0
      _call(scenario, "DelegateTaskService", "correctDelegateTaskScope",
        Vector("--input", _json(correction))).business[V.DelegateTaskScopeCorrectionView].outcome shouldBe "already-corrected"
      scenario.candidateHead shouldBe head
      scenario.candidateIndexDiff shouldBe index
      scenario.candidateStatus shouldBe status
      Files.readAllBytes(repository.resolve(".git/index")).toVector shouldBe indexbytes
      Files.readString(repository.resolve("docs/phase.md")) shouldBe document
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
      Files.exists(scenario.root.resolve("publication-count.txt")) shouldBe false
      val commands = scenario.invocations.filter(_.argv.head == _executable.toString)
      commands.foreach(invocation => invocation.argv(1) should startWith("org.simplemodeling.textus.SmWorkflow."))
      val paths = scenario.root.resolve("scope-correction-result-paths.json")
      Files.writeString(paths, Json.obj("provenance" -> "SPECIFICATION-OWNED".asJson,
        "sessionId" -> started.sessionId.asJson, "oldContinuationId" -> oldcontext.continuationId.asJson,
        "newContinuationId" -> next.issuedWorkContext.get.continuationId.asJson,
        "invocations" -> commands.map(invocation => Json.obj("stdout" -> invocation.stdout.toString.asJson,
          "stderr" -> invocation.stderr.toString.asJson, "pid" -> invocation.pid.asJson)).asJson).spaces2)
      info("SPECIFICATION-OWNED installed correction evidence: " + paths)
    }
  }
}
