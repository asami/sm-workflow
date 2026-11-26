package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.ZipFile
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
  * @since   Nov.  5, 2026
  * @version Nov. 23, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class NativeDelegateTaskScopeCorrectionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _candidateFacade(scenario: F.Scenario): Path = {
    val executable = sys.props.get("smworkflow.spec.cncfCommand").orElse(sys.env.get("SM_WORKFLOW_SPEC_CNCF_COMMAND"))
      .map(Path.of(_)).getOrElse {
        sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator).iterator.filter(_.nonEmpty)
          .map(x => Path.of(x).toAbsolutePath.normalize.resolve("cncf"))
          .find(x => Files.isRegularFile(x) && Files.isExecutable(x)).get
      }
    val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim)
    val archive = new ZipFile(car.toFile)
    val minimum = try {
      val stream = archive.getInputStream(archive.getEntry("car-runtime-manifest.json"))
      val content = try new String(stream.readAllBytes(), UTF_8) finally stream.close()
      parser.parse(content).toOption.get.hcursor.downField("runtime").downField("cncf").get[String]("minimum").toOption.get
    } finally archive.close()
    val runtime = sys.props.get("smworkflow.spec.cncfRuntime").orElse(sys.env.get("SM_WORKFLOW_SPEC_CNCF_RUNTIME"))
      .orElse(Some(minimum))
    F.PackagedInstalled(executable, car, runtime) // Validate the selected installed entry and candidate archive.
    def shell(value: String) = "'" + value.replace("'", "'\\''") + "'"
    val facade = scenario.root.resolve("candidate-cli")
    val prefix = Vector(executable.toString) ++ F.runtimeArguments(runtime) ++ Vector(car.toString,
      "command", "--force-exit", "--no-default-components")
    Files.writeString(facade, "#!/bin/sh\nexec " + prefix.map(shell).mkString(" ") + " \"$@\"\n", UTF_8)
    require(facade.toFile.setExecutable(true), "Cannot enable candidate CLI")
    facade
  }
  private var _executable: Path = _
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _state(view: V.DelegateTaskView): S.State =
    GoalPhaseNativeMetadata.normalizePublicJson(parser.parse(take(view.state.get.toJsonStringC)).toOption.get)
      .toOption.get.as[S.State].toOption.get
  private def _call(scenario: F.Scenario, service: String, operation: String, parameters: Vector[String]): F.Result =
    scenario.callInstalled(_executable, service, operation, parameters, installations = None,
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")),
      wait = Some(F.InstalledCallWait(180000L, 5000L)))
  private def _view(scenario: F.Scenario, result: F.Result): V.DelegateTaskView = {
    val control = result.business[V.DelegateTaskView]
    val work = control.retainedWork.map { reference =>
      _call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
        Vector("--input", _json(reference))).business[V.WorkflowRetainedWorkReadResult].response
    }.getOrElse(control.toRecord())
    val complete = control.retainedMaterials.foldLeft(work) { (prior, field) =>
      val detail = _call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedSnapshot.readOperation,
        Vector("--input", _json(field.reference))).business[V.WorkflowRetainedSnapshotReadResult]
      val overlay = control.controlSummary.flatMap(_.getRecord(field.fieldName)).getOrElse(org.goldenport.record.Record.empty)
      val material = org.goldenport.record.Record.createFull(detail.material.fields.filterNot(value => overlay.getAny(value.key).nonEmpty)
        .map(value => value.key -> value.value.single)) ++ overlay
      org.goldenport.record.Record.createFull(prior.fields.filterNot(_.key == field.fieldName)
        .map(value => value.key -> value.value.single)) ++ org.goldenport.record.Record.data(field.fieldName -> material)
    }
    take(V.DelegateTaskView.createC(complete))
  }

  "Installed pending destination correction" should {
    "admit an independently selected timing subset on the same Session and reject the retired worker without executing checks or publication" in {
      Given("a fresh tiny destination with the existing concrete SPECIFICATION-OWNED DesignStandard profile")
      val parameters = BoundedWorkExecutionAdmissionFixture.parameters
      val mapping = parser.parse(parameters(WorkflowExecutionProfileParameters.declaration.name)).toOption.get
      val origin = parser.parse(parameters(GoalPhaseNativeExecution.declaration.name)).toOption.get
      val scenario = F.fresh("installed-delegate-scope-correction", bootstrapSelectedInput = false,
        mappedExecution = Some(mapping -> origin))
      val configfile = scenario.root.resolve(".textus/config.json")
      val config = parser.parse(Files.readString(configfile)).toOption.get
      val originalPlan = parser.parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      val standardPlan = NativeCommonDelegateTaskFixture.processPlan(scenario.root, focused = false)
      def rows(plan: Json, key: String): Vector[Json] = plan.hcursor.get[Vector[Json]](key).toOption.get
      // Keep the prepared-import workspace while using the ordinary standard
      // repository family and its fixed capabilities for this host.
      val preparedWorkspace = rows(originalPlan, "workspaces").filter(_.hcursor.get[String]("identity").contains("prepared-imports"))
      val preparedGrant = rows(originalPlan, "workspaceGrants").filter(_.hcursor.get[String]("workspaceId").contains("prepared-imports"))
      val preparedIds = preparedWorkspace.map(_.hcursor.get[Json]("identity").toOption.get)
      val initializations = rows(standardPlan, "workspaceInitializations").map(_.mapObject(fields =>
        fields.add("sources", Json.fromValues(fields("sources").get.asArray.get ++ preparedIds))))
      val plan = standardPlan.mapObject(_.add("workspaces", Json.fromValues(rows(standardPlan, "workspaces") ++ preparedWorkspace))
        .add("workspaceGrants", Json.fromValues(rows(standardPlan, "workspaceGrants") ++ preparedGrant))
        .add("workspaceInitializations", Json.fromValues(initializations)))
      Files.writeString(configfile, config.mapObject(_.remove("sm-workflow.runtime.definition")
        .add("textus.subsystem.process-execution.plan", plan.noSpaces.asJson)).noSpaces)
      _executable = _candidateFacade(scenario)
      val request = V.DelegateTaskInput(scenario.host.goal.input.repositoryIdentity, "installed-condition-cycle",
        "SPECIFICATION-OWNED retain the tiny selected document and qualify delivery later",
        NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.of("behavior", "coverage", "preservation", "delivery"),
        NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-request", "1")), false,
        "documentation", "off", 10, "direct-instruction", Vector.empty,
        V.DelegateTaskContract("SPECIFICATION-OWNED-requester", "SPECIFICATION-OWNED-destination",
          "SPECIFICATION-OWNED-requester", "org.example.ScopeCorrectionFixture", "destination", "documentation",
          NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
          Vector("remote-push"), NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-Human", "1")),
          None, Vector.empty, "SPECIFICATION-OWNED fixture; actual consumer remains untouched", None, None, None),
        Vector("behavior", "coverage", "preservation"), None)
      val repository = Path.of(scenario.host.target.root)
      val document = Files.readString(repository.resolve("docs/phase.md"))
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
      def _advance_() = _view(scenario, _call(scenario, "DelegateTaskService", "advanceDelegateTask",
        Vector("--sessionId", started.sessionId)))
      val initial = _advance_()
      val originalreference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-original-unresolved", "1")
      val originalresult = V.DelegateTaskWorkResult("decision-needed", "SPECIFICATION-OWNED dependency unresolved", Vector.empty,
        NonEmptyVector.of(V.DelegateTaskCondition("behavior", false, "await direction"),
          V.DelegateTaskCondition("coverage", false, "await direction"),
          V.DelegateTaskCondition("preservation", false, "await direction"),
          V.DelegateTaskCondition("delivery", false, "await direction")),
        Vector("SPECIFICATION-OWNED dependency requires direction"), None, None)
      val originalexecution = V.WorkflowExecutionEvidence(Vector(originalreference), Some("SPECIFICATION-OWNED-worker"), None,
        Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
          BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity)))
      _call(scenario, "DelegateTaskService", "submitDelegateTaskWorkResult", Vector("--sessionId", started.sessionId,
        "--issuedWorkContext", _json(initial.issuedWorkContext.get), "--result", _json(originalresult),
        "--resultReference", _json(originalreference), "--executionEvidence", _json(originalexecution))).exitCode shouldBe 0
      _state(_advance_()).stage shouldBe "Human"
      val semanticdescription = _call(scenario, "DelegateTaskService", "describeDelegateTaskSemanticContinuation",
        Vector("--input", _json(V.DelegateTaskSemanticContinuationDescriptionInput(started.sessionId))))
        .business[V.DelegateTaskSemanticContinuationDescriptionResult]
      val semantictarget = take(DelegateTaskSemanticContinuation.targetC(semanticdescription.target))
      semantictarget.original.resultReference.identity shouldBe originalreference.identity
      semantictarget.original.resultReference.revision shouldBe originalreference.revision
      val humanreference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-semantic-Human", "1")
      val direction = V.DelegateTaskSemanticContinuationInput(started.sessionId, semanticdescription.target,
        "ContinueAfterDependencyResolution", "SPECIFICATION-OWNED direction", "participant", humanreference,
        Vector(V.WorkflowArtifactReference("SPECIFICATION-OWNED-upstream", "1")), None)
      Given("the same guard with actual-shaped retained data and independently varied blockers")
      val retaineddirection = DelegateTaskSemanticContinuation.Direction(parser.parse(direction.target).toOption.get,
        direction.choice, direction.rationale, direction.participant,
        org.goldenport.cncf.workflow.ContextReference(humanreference.identity, humanreference.revision),
        Vector(org.goldenport.cncf.workflow.ContextReference("SPECIFICATION-OWNED-upstream", "1")))
      val original = semantictarget.original
      val retainedreference = original.resultReference
      def _matches_(target: DelegateTaskSemanticContinuation.Target, originalvalue: DelegateTaskSemanticContinuation.Original,
        ref: org.goldenport.cncf.workflow.ContextReference, selected: DelegateTaskSemanticContinuation.Direction,
        owner: String, session: String): Boolean =
        DelegateTaskImplementationContinuation.matchesOriginal(ref, selected, target, originalvalue, owner, session)
      _matches_(semantictarget, original, retainedreference, retaineddirection, semantictarget.componentInstance, started.sessionId) shouldBe true
      _matches_(semantictarget, original, retainedreference.copy(identity = "foreign"), retaineddirection, semantictarget.componentInstance, started.sessionId) shouldBe false
      _matches_(semantictarget, original.copy(continuationId = "foreign"), retainedreference, retaineddirection, semantictarget.componentInstance, started.sessionId) shouldBe false
      _matches_(semantictarget, original.copy(sourceRevision = original.sourceRevision + 1), retainedreference, retaineddirection, semantictarget.componentInstance, started.sessionId) shouldBe false
      _matches_(semantictarget, original.copy(resultReference = retainedreference.copy(revision = "foreign")), retainedreference, retaineddirection, semantictarget.componentInstance, started.sessionId) shouldBe false
      _matches_(semantictarget, original, retainedreference, retaineddirection.copy(choice = "Defer"), semantictarget.componentInstance, started.sessionId) shouldBe false
      _matches_(semantictarget, original, retainedreference, retaineddirection, "foreign", started.sessionId) shouldBe false
      _matches_(semantictarget, original, retainedreference, retaineddirection, semantictarget.componentInstance, "foreign") shouldBe false
      val business = semantictarget.business
      DelegateTaskImplementationContinuation.withoutExecutionEvidence(business) shouldBe true
      Vector(
        business.state.copy(candidate = Some(S.Candidate("head", "tree", Vector("value.txt"), "diff"))),
        business.state.copy(pendingCheck = Some("test")),
        business.state.copy(reviewReference = Some(retainedreference)),
        business.state.copy(commit = Some(S.Commit("head", "parent", "tree")))
      ).foreach(state => DelegateTaskImplementationContinuation.withoutExecutionEvidence(business.copy(state = state)) shouldBe false)
      When("a separate Human direction is prepared and admitted")
      val humanexecution = V.WorkflowExecutionEvidence(Vector(humanreference), Some("participant"), None, None)
      def _save_(name: String, value: RecordPresentable): String = {
        val path = scenario.root.resolve(name + ".json")
        Files.writeString(path, _json(value))
        path.toUri.toASCIIString
      }
      val evidencepreparation = V.WorkflowEvidencePreparationInput("DelegateTask", "semantic-continuation",
        _save_("semantic-description", semanticdescription), _save_("semantic-direction", direction),
        _save_("semantic-reference", humanreference), _save_("semantic-execution", humanexecution),
        "normal-development-host", java.time.Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence", Some("participant"))
      val evidenceprepared = _call(scenario, "NativeWorkflowPreparationService", "prepareEvidence",
        Vector("--input", _json(evidencepreparation)))
      scenario.selectPreparedImport(evidenceprepared)
      _call(scenario, "DelegateTaskService", "resumeDelegateTaskSemanticContinuation",
        Vector("--input", _json(direction))).business[V.DelegateTaskSemanticContinuationDescriptionResult].outcome shouldBe "resumed-awaiting-advance"
      val old = _advance_()
      val before = _state(old)
      val oldcontext = old.issuedWorkContext.get
      Then("normal Advance retains the exact initial Implement assignment")
      before.stage shouldBe "Implement"
      before.semanticReference.get.identity shouldBe originalreference.identity
      before.semanticReference.get.revision shouldBe originalreference.revision
      old.issuedWorkContext.get.continuationId should not be initial.issuedWorkContext.get.continuationId
      old.kind shouldBe "Request"
      before.input.conditions shouldBe Vector("behavior", "coverage", "preservation", "delivery")
      before.input.submissionConditions shouldBe Some(Vector("behavior", "coverage", "preservation"))

      When("the public structured description names this still-unclaimed issue and the selected submission IDs")
      val descriptioninput = V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, oldcontext.continuationId,
        Vector("value.txt"), Vector.empty, None)
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
      def git(args: String*): Unit = {
        val command = Vector("/usr/bin/git", "-C", repository.toString, "-c", "user.name=Fixture",
          "-c", "user.email=fixture@example.invalid", "-c", "core.hooksPath=/dev/null") ++ args
        scala.sys.process.Process(command).! shouldBe 0
      }
      git("checkout", "-b", "incompatible")
      _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", started.sessionId)).exitCode should not be 0
      git("checkout", "main")
      Files.writeString(repository.resolve("docs/user.md"), "Original user document\n")
      git("add", "--", "docs/user.md")
      git("commit", "--no-verify", "--no-gpg-sign", "-m", "Independent user document baseline")
      Files.writeString(repository.resolve("docs/phase.md"), document + "partial implementation\n")
      git("add", "--", "docs/phase.md")
      Files.writeString(repository.resolve("docs/user.md"), "unrelated documentation\n")
      val head = scenario.candidateHead
      val index = scenario.candidateIndexDiff
      val status = scenario.candidateStatus
      val indexbytes = Files.readAllBytes(repository.resolve(".git/index")).toVector
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
      after.input.submissionConditions shouldBe Some(Vector("behavior", "coverage", "preservation"))
      after.input.ownedPaths shouldBe before.input.ownedPaths :+ "value.txt"
      after.input.copy(ownedPaths = before.input.ownedPaths, sourceReferences = before.input.sourceReferences) shouldBe before.input
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
          V.DelegateTaskCondition("coverage", true, "SPECIFICATION-OWNED submission"),
          V.DelegateTaskCondition("preservation", true, "SPECIFICATION-OWNED submission"),
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
      Files.readString(repository.resolve("docs/phase.md")) shouldBe document + "partial implementation\n"
      Files.readString(repository.resolve("docs/user.md")) shouldBe "unrelated documentation\n"
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
