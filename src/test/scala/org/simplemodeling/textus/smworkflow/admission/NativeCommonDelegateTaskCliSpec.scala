package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take
import StandaloneFixExecution as S
import S.given

/** Purpose-owned bounded output producer; selected explicitly by the tiny sbt
  * metadata build, never discovered in the owning repository's ordinary suite.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskTestOnlyOutputFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Selected recovery input" should {
    "retain its real body invocation under a finite output policy" in {
      Given("one isolated metadata build and its explicit specification-owned output switch")
      val repository = Path.of("").toAbsolutePath.normalize
      val root = repository.getParent
      When("the actual selected body observes the fixture input")
      Files.writeString(root.resolve("selected-body-executions.txt"), java.lang.ProcessHandle.current().pid().toString + "\n",
        StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      if (Files.exists(root.resolve("flood-enabled"))) {
        System.out.print("D" * (2 * 1024 * 1024))
        System.out.flush()
      }
      val actual = Files.readString(repository.resolve("value.txt"))
      Then("the actual product input remains selected and successful when its report is complete")
      actual shouldBe "expected\n"
    }
  }
}

/** Inputs for the common native pipeline, with no manufactured process receipt.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  */
private[admission] object DelegateTaskTestOnlyRecoveryFixture {
  val suite = classOf[DelegateTaskTestOnlyOutputFixtureSpec].getName
  val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("fix-check", 2, WorkflowSbtValidation.Kind.FixCheck),
    GoalPhaseNativeValidation.Check("test-only", 3, WorkflowSbtValidation.Kind.TestOnly))
  def build(root: Path, selectedClasspath: Option[Path] = None): Unit = {
    TypedSbtHostPlanFixture.build(root, successful = true)
    TypedSbtHostPlanFixture.buildMetadata(root, classOf[DelegateTaskTestOnlyOutputFixtureSpec], selectedClasspath)
    Files.writeString(root.resolve("repository/value.txt"), "initial\n")
    Files.writeString(root.resolve("repository/build.sbt"), """
      |lazy val fixCheck = taskKey[Unit]("Check the actual isolated product input")
      |fixCheck := {
      |  IO.append(baseDirectory.value.getParentFile / "fix-check-executions.txt", "executed\n")
      |  if (IO.read(baseDirectory.value / "value.txt") != "expected\n") sys.error("SPEC actual input differs")
      |}
      |""".stripMargin, StandardOpenOption.APPEND)
    Files.writeString(root.resolve("flood-enabled"), "SPECIFICATION-OWNED bounded 2 MiB output producer\n")
    Files.createDirectories(root.resolve("capture-root"))
  }
  def processPlan(root: Path, git: Json): Json = {
    def _array_(value: Json, name: String) = value.hcursor.get[Vector[Json]](name).toOption.get
    // Reuse typed Git authority only; replace the old fixture's SBT workspaces
    // together with its programs so each ordinal remains uniquely declared.
    val oldtargets = _array_(git, "sbtPrograms").flatMap(_.hcursor.get[String]("target").toOption).toSet
    val gitonly = git.mapObject(_.add("sbtPrograms", Json.arr())
      .add("workspaces", Json.fromValues(_array_(git, "workspaces").filterNot(_.hcursor.get[String]("identity").toOption.exists(oldtargets))))
      .add("workspaceGrants", Json.fromValues(_array_(git, "workspaceGrants").filterNot(_.hcursor.get[String]("workspaceId").toOption.exists(oldtargets)))))
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(gitonly), operation = SbtOperation.Compile)
    val lint = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2), operation = SbtOperation.FixCheck)
    val selected = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(3),
      operation = SbtOperation.TestOnly, testClasses = Vector(suite), stdoutBytes = 786432, stderrBytes = 16384)
    val programs = _array_(compile, "sbtPrograms") ++ _array_(lint, "sbtPrograms") ++ _array_(selected, "sbtPrograms")
    val capabilities = (_array_(compile, "gitPrograms") ++ programs).map(_.hcursor.get[Json]("capability").toOption.get)
    compile.mapObject(_.add("sbtPrograms", programs.asJson)
      .add("workspaces", Json.fromValues(_array_(compile, "workspaces") ++ _array_(lint, "workspaces") ++ _array_(selected, "workspaces") :+
        Json.obj("identity" -> "sm-workflow-validation-artifacts".asJson, "root" -> root.resolve("capture-root").toString.asJson)))
      .add("workspaceGrants", Json.fromValues(_array_(compile, "workspaceGrants") ++ _array_(lint, "workspaceGrants") ++ _array_(selected, "workspaceGrants") :+
        Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson, "workspaceId" -> "sm-workflow-validation-artifacts".asJson,
          "access" -> "manage".asJson)))
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson, "capabilities" -> capabilities.asJson))))
  }
}

/** Tiny build and ordinary host declarations only. The fixed focused suite is
  * host configuration, never a request-supplied class or command argument.
  * @since   Nov.  5, 2026
  * @version Nov. 19, 2026
  */
private[admission] object NativeCommonDelegateTaskFixture extends ThreeWorkflowManagedRepositoryFixture {
  val suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  val testPath = "src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala"
  val component = "org.example.CommonDelegateFixture"
  val publication = "common-delegate-fixture"

  def processPlan(root: Path, focused: Boolean = true): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("runtime.root" -> root.resolve("repository").toString, "runtime.parent" -> root.toString,
      "repository.root" -> root.resolve("repository").toString, "git.branch" -> "main",
      "git.remote" -> "origin", "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "executable.sbt" -> TypedSbtHostPlanFixture.normalExecutable.toString,
      "environment.path" -> sys.env.getOrElse("PATH", "/usr/bin:/bin"),
      "environment.java-home" -> System.getProperty("java.home"), "environment.ssh-auth-sock" -> "")
    def _expand_(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior, (key, replacement)) =>
      prior.replace("${" + key + "}", replacement) }).mapArray(_.map(_expand_)).mapObject(_.mapValues(_expand_))
    val base = _expand_(template)
    if (!focused) base else {
      val program = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2),
        operation = SbtOperation.TestOnly, testClasses = Vector(suite))
        .hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head
      base.mapObject(fields => fields.add("sbtPrograms", fields("sbtPrograms").get.mapArray(_ :+ program))
        .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          grant("capabilities").get.mapArray(_ :+ program.hcursor.get[Json]("capability").toOption.get)))))))
    }
  }
  /** Each selected detail is another actual installed process. Keep ordinary
    * control available separately for output and route assertions below. */
  def view(scenario: F.Scenario, result: F.Result, call: (F.Scenario, String, String, Vector[String]) => F.Result): V.DelegateTaskView = {
    val control = result.business[V.DelegateTaskView]
    val work = control.retainedWork.map { reference =>
      call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
        Vector("--input", take(reference.toRecord().toJsonStringC))).business[V.WorkflowRetainedWorkReadResult].response
    }.getOrElse(control.toRecord())
    val complete = control.retainedMaterials.foldLeft(work) { (prior, field) =>
      val detail = call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedSnapshot.readOperation,
        Vector("--input", take(field.reference.toRecord().toJsonStringC))).business[V.WorkflowRetainedSnapshotReadResult]
      val overlay = control.controlSummary.flatMap(_.getRecord(field.fieldName)).getOrElse(org.goldenport.record.Record.empty)
      val material = org.goldenport.record.Record.createFull(detail.material.fields.filterNot(value => overlay.getAny(value.key).nonEmpty)
        .map(value => value.key -> value.value.single)) ++ overlay
      org.goldenport.record.Record.createFull(prior.fields.filterNot(_.key == field.fieldName)
        .map(value => value.key -> value.value.single)) ++ org.goldenport.record.Record.data(field.fieldName -> material)
    }
    take(V.DelegateTaskView.createC(complete))
  }

  def fresh(componentFile: Option[Path]): F.Scenario = {
    val scenario = F.fresh("installed-common-delegate", bootstrapSelectedInput = false,
      sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
    val root = scenario.root
    Files.createDirectories(root.resolve("isolated-home/.textus"))
    val repository = Path.of(scenario.host.target.root)
    TypedSbtHostPlanFixture.buildAuthoring(root)
    Files.writeString(repository.resolve("build.sbt"),
      "\norganization := \"org.example.sm-workflow-common-delegate-fixture\"\n" +
        "name := \"common-delegate-fixture\"\nversion := \"0.0.1-SNAPSHOT\"\n" +
        "publishLocal := { val result = publishLocal.value; IO.append(file(\"../publication-count.txt\"), \"published\\n\"); result }\n",
      StandardOpenOption.APPEND)
    // Fixed fixture baseline only, before any business request. Runtime owns the
    // later selected commit; these setup calls do not create native receipts.
    take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", ".gitignore", "build.sbt",
      "project/build.properties", "src/main", "docs")))
    take(_run_setup_command_(Vector("-C", repository.toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "-m", "Configure tiny common delegation fixture")))
    val configfile = root.resolve(".textus/config.json")
    val config = parser.parse(Files.readString(configfile)).toOption.get
    val collection = ThreeWorkflowNormalHostFixture.collection("local_publication")
    val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
      fields("records").get.mapArray(_ :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "storeName" -> "workflow".asJson, "collection" -> collection.asJson))))
    val policy = Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
      "targets" -> Json.arr(Json.obj("identity" -> NativeCommonDelegateTaskFixture.publication.asJson,
        "component" -> NativeCommonDelegateTaskFixture.component.asJson,
        "workspace" -> TypedGitHostPlanFixture.workspace.asJson)))
    // Both host plans below replace F.fresh's plans, including its importer
    // collection/workspace grants. Rebuild that declaration against these
    // selected plans before the first public preparation call.
    val selected = config.mapObject(fields => fields.remove(ThreeWorkflowRuntimeParameters.definition.name)
      .remove(CommonEvidence.declaration.name)
      .add("textus.subsystem.process-execution.plan", NativeCommonDelegateTaskFixture.processPlan(root).noSpaces.asJson)
      .add("textus.subsystem.storage.plan", storage.noSpaces.asJson)
      .add(LocalPublicationRuntime.declaration.name, policy.noSpaces.asJson))
    val logical = BoundedWorkExecutionAdmissionFixture.logicalParameters.foldLeft(selected) { case (prior, (key, value)) =>
      prior.mapObject(_.add(key, value.asJson))
    }
    val installed = componentFile.fold(logical) { component =>
      require(component.isAbsolute && component.normalize == component && Files.isRegularFile(component),
        "Select an actual absolute Component archive")
      logical.mapObject(_.add("textus.component.file", component.toString.asJson))
    }
    Files.writeString(configfile, installed.noSpaces)
    scenario.enablePreparedImportRetention()
    scenario
  }
}

/** Explicit installed qualification. Human selections, transport observations
  * and semantic judgments are SPECIFICATION-OWNED. Actual checks, commit and
  * tiny artifact publication belong to the normal destination runtime.
  */
private[admission] trait NativeCommonDelegateTaskQualification extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  protected def commonDelegateExecutable: Path
  protected def commonDelegateInstallations: Option[Path] = None
  protected def commonDelegateComponentFile: Option[Path] = sys.props.get("smworkflow.spec.componentFile")
    .orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE")).map(Path.of(_))
  protected def commonDelegateCall(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    scenario.callInstalled(commonDelegateExecutable, service, operation, arguments, installations = commonDelegateInstallations,
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _state(view: V.DelegateTaskView): S.State = GoalPhaseNativeMetadata.normalizePublicJson(
    parser.parse(take(view.state.get.toJsonStringC)).toOption.get).toOption.get.as[S.State].toOption.get
  private def _work(view: V.DelegateTaskView): S.Work = GoalPhaseNativeMetadata.normalizePublicJson(
    parser.parse(take(view.work.get.toJsonStringC)).toOption.get).toOption.get.as[S.Work].toOption.get
  private def _call(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    commonDelegateCall(scenario, service, operation, arguments)

  private def _view(scenario: F.Scenario, result: F.Result): V.DelegateTaskView =
    NativeCommonDelegateTaskFixture.view(scenario, result, commonDelegateCall)

  private def _fresh(): F.Scenario = NativeCommonDelegateTaskFixture.fresh(commonDelegateComponentFile)

  protected def qualifyCommonDelegate(outcome: String): Unit = {
      Given("one tiny isolated normal host with no runtime.definition and SPECIFICATION-OWNED request authority")
      val scenario = _fresh()
      val root = scenario.root
      val config = parser.parse(Files.readString(root.resolve(".textus/config.json"))).toOption.get
      config.hcursor.get[String](ThreeWorkflowRuntimeParameters.definition.name).toOption shouldBe None
      val conditions = NonEmptyVector.of("behavior", "validation", "commit", "publication")
      val request = V.DelegateTaskInput(scenario.host.goal.input.repositoryIdentity, "installed-common-request",
        "SPECIFICATION-OWNED author tiny LineWriter behavior and return the genuinely published source",
        NonEmptyVector.one(NativeCommonDelegateTaskFixture.testPath), NonEmptyVector.of("compile", "test-only"), conditions,
        NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-original-request", "1")), false,
        "code", "off", 10, "direct-instruction", Vector.empty,
        V.DelegateTaskContract("SPECIFICATION-OWNED-requester", "SPECIFICATION-OWNED-destination",
          "SPECIFICATION-OWNED-requester", NativeCommonDelegateTaskFixture.component, "requester", "localized",
          NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt", "publishLocal"),
          Vector("remote-push"), NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-Human", "1")),
          Some(NativeCommonDelegateTaskFixture.publication),
          Vector("org.example.sm-workflow-common-delegate-fixture:common-delegate-fixture_3:0.0.1-SNAPSHOT"),
          "SPECIFICATION-OWNED artifact return; consumer selection and activation remain separate"), Vector.empty)
      def _start_(input: V.DelegateTaskInput): V.DelegateTaskView = {
        val role = input.delegation.role
        val file = root.resolve(role + "-input.json")
        Files.writeString(file, _json(input))
        val preparation = V.WorkflowDelegateTaskStartPreparationInput(file.toUri.toASCIIString,
          role + "-selection", "participant", "ExplicitHuman", role + "-key", "entity:smworkflow.cli.goal_selection")
        val prepared = _call(scenario, "NativeWorkflowPreparationService", "prepareDelegateTaskStart", Vector("--input", _json(preparation)))
        scenario.selectPreparedImport(prepared)
        _view(scenario, _call(scenario, "DelegateTaskService", "startDelegateTask", Vector("--input", _json(input),
          "--selectionId", role + "-selection", "--participant", "participant", "--idempotencyKey", role + "-key")))
      }
      def _advance_(id: String) = _view(scenario, _call(scenario, "DelegateTaskService", "advanceDelegateTask",
        Vector("--sessionId", id)))
      def _observe_(id: String, event: String, destination: String, result: Option[org.goldenport.record.Record] = None) =
        _view(scenario, _call(scenario, "DelegateTaskDeliveryService", "observeDelegateTaskDelivery", Vector("--input", _json(
          V.DelegateTaskObservationInput(id, event, V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + event, "1"),
            Some(destination), result, None, "SPECIFICATION-OWNED transport observation; no consumer effect is certified")))))
      def _submit_(view: V.DelegateTaskView, result: V.DelegateTaskWorkResult, name: String): F.Result = {
        val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + name, "1")
        val worker = "SPECIFICATION-OWNED-" + name
        val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None,
          Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", worker, "ordinary")))
        _call(scenario, "DelegateTaskService", "submitDelegateTaskWorkResult", Vector("--sessionId", view.sessionId,
          "--issuedWorkContext", _json(view.issuedWorkContext.get), "--result", _json(result),
          "--resultReference", _json(reference), "--executionEvidence", _json(evidence)))
      }
      When("requester and destination select the same complete request and default timing through public preparation and Start")
      val requester = _start_(request)
      val dispatch = _advance_(requester.sessionId)
      val destination = _start_(request.copy(delegation = request.delegation.copy(role = "destination")))
      val implement = _advance_(destination.sessionId)
      withClue("Recovery qualification requires the actual selected candidate Component archive: ") {
        commonDelegateComponentFile should not be empty
      }
      val help = _call(scenario, "meta", "help", Vector(SmWorkflowComponent.componentId.name + ".DelegateTaskService"))
      help.exitCode shouldBe 0
      val helpText = Files.readString(help.invocation.stdout)
      helpText should include("describeDelegateTaskValidationRecovery")
      helpText should include("recoverDelegateTaskValidation")
      // Actual installed handlers must reject an active issued Implement frontier;
      // declaration-only Help or failure to resolve the operation is insufficient.
      val description = _call(scenario, "DelegateTaskService", "describeDelegateTaskValidationRecovery",
        Vector("--input", _json(V.DelegateTaskValidationRecoveryDescriptionInput(destination.sessionId))))
      description.exitCode should not be 0
      (Files.readString(description.invocation.stdout) + Files.readString(description.invocation.stderr)) should
        include("checkpoint")
      val recovery = _call(scenario, "DelegateTaskService", "recoverDelegateTaskValidation",
        Vector("--input", _json(V.DelegateTaskValidationRecoveryInput(destination.sessionId, "{}", "{}", "participant",
          V.WorkflowArtifactReference("SPECIFICATION-OWNED-unadmitted", "1")))))
      recovery.exitCode should not be 0
      (Files.readString(recovery.invocation.stdout) + Files.readString(recovery.invocation.stderr)) should
        include("checkpoint")
      val dispatched = _observe_(requester.sessionId, "dispatch-observed", destination.sessionId)
      Then("requester transport preserves explicit empty timing and destination work does not wait for later effects")
      dispatch.kind shouldBe "Dispatch"
      _state(dispatched).stage shouldBe "AwaitResult"
      _state(dispatched).input.submissionConditions shouldBe Some(Vector.empty)
      _state(implement).input.submissionConditions shouldBe Some(Vector.empty)
      _work(implement).kind shouldBe "Implement"
      _work(implement).operatingProfile.get.dispatch.get.candidates shouldBe Vector("ordinary", "difficult", "hardest")
      _advance_(destination.sessionId).issuedWorkContext shouldBe implement.issuedWorkContext
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      Files.exists(root.resolve("publication-count.txt")) shouldBe false
      val beforehead = scenario.candidateHead
      val path = Path.of(scenario.host.target.root).resolve(NativeCommonDelegateTaskFixture.testPath)
      Files.createDirectories(path.getParent)
      Files.writeString(path, """package org.simplemodeling.textus.smworkflow.p62authored
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.GivenWhenThen
        |final class LineWriterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "LineWriter" should { "preserve each selected text before one terminator" in {
        |    Given("ASCII Unicode and empty text")
        |    val inputs = Vector("expected", "日本語", "")
        |    When("each input is rendered")
        |    val rendered = inputs.map(LineWriter.render)
        |    Then("the original text is followed by exactly one LF")
        |    rendered shouldBe inputs.map(_ + "\n")
        |  }}
        |}
        |""".stripMargin)
      val deferredreport = conditions.toVector.map(id => V.DelegateTaskCondition(id, false,
        "Actual runtime validation local commit and publication have not yet established this obligation"))
      val completed = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED tiny behavior supplied for genuine later verification",
        Vector.empty, NonEmptyVector.fromVectorUnsafe(deferredreport), Vector.empty, None, None)
      When("the destination submits a complete honest report with later effects false")
      val submitted = _submit_(implement, completed, "implementer")
      Then("implementation is accepted before those checks commit or publication execute")
      withClue(submitted.invocation.logs) { submitted.exitCode shouldBe 0 }
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      scenario.candidateHead shouldBe beforehead
      var current = _advance_(destination.sessionId)
      var remaining = 12
      while (current.work.isEmpty && remaining > 0 && current.kind != "Return") {
        current = _advance_(destination.sessionId)
        remaining -= 1
      }
      Then("ordinary destination owners execute fixed Compile TestOnly local commit and actual tiny publication before final judgment")
      withClue(current.message) { current.work should not be empty }
      val acceptedwork = _work(current)
      acceptedwork.kind shouldBe "DeliveryAcceptance"
      acceptedwork.state.conditionReport.map(_.satisfied) shouldBe Vector.fill(4)(false)
      acceptedwork.state.receipts.map(_.identity) shouldBe Vector("compile", "test-only")
      acceptedwork.state.receipts.foreach(_.exitCode shouldBe Some(0))
      acceptedwork.state.commit.get.identity shouldBe scenario.candidateHead
      scenario.candidateHead should not be beforehead
      acceptedwork.state.delivery.get.publication.get.ready shouldBe true
      acceptedwork.state.delivery.get.publication.get.request.source.head shouldBe scenario.candidateHead
      acceptedwork.state.reviewReference shouldBe None // Conditional detailed review was not required for this bounded request.
      val checks = Files.readString(root.resolve("sbt-executions.txt"))
      val publications = Files.readString(root.resolve("publication-count.txt"))
      publications.linesIterator.toVector shouldBe Vector("published")
      val commands = Files.readString(root.resolve("sbt-command-lines.txt"))
      commands should include("testOnly " + NativeCommonDelegateTaskFixture.suite)
      val finalreport = deferredreport.map(c => c.copy(satisfied = true,
        detail = "SPECIFICATION-OWNED judgment binds the retained actual selected checks source commit and publication"))
      val finalresult = completed.copy(outcome = "accept", conditions = NonEmptyVector.fromVectorUnsafe(finalreport))
      When("final acceptance omits an original ID or falsely leaves an obligation unresolved")
      val missing = _submit_(current, finalresult.copy(conditions = NonEmptyVector.fromVectorUnsafe(finalreport.dropRight(1))), "final-missing")
      val unfinished = _submit_(current, finalresult.copy(conditions = NonEmptyVector.fromVectorUnsafe(
        finalreport.updated(3, deferredreport(3)))), "final-unfinished")
      Then("both refuse without replaying the retained effects")
      missing.exitCode should not be 0
      unfinished.exitCode should not be 0
      _advance_(destination.sessionId).issuedWorkContext shouldBe current.issuedWorkContext
      if (outcome == "decision-needed") {
        When("DeliveryAcceptance honestly retains unresolved activation obligations after actual commit and publication")
        val decision = finalresult.copy(outcome = "decision-needed", conditions = NonEmptyVector.fromVectorUnsafe(deferredreport),
          unresolvedQuestions = Vector("SPECIFICATION-OWNED activation remains unresolved"))
        val submitteddecision = _submit_(current, decision, "delivery-decision")
        withClue(submitteddecision.invocation.logs) { submitteddecision.exitCode shouldBe 0 }
        val submittedcontrol = submitteddecision.business[V.DelegateTaskView]
        submittedcontrol.kind shouldBe "Submitted"
        submittedcontrol.state shouldBe empty
        submittedcontrol.retainedMaterials.map(_.fieldName) should contain("state")
        val humanresult = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", destination.sessionId))
        val humancontrol = humanresult.business[V.DelegateTaskView]
        Then("ordinary Human control uses its exact state owner without a fabricated completion operation or ReturnReady")
        humancontrol.kind shouldBe "Human"
        humancontrol.state shouldBe empty
        humancontrol.retainedWork shouldBe empty
        humancontrol.issuedWorkContext shouldBe empty
        humancontrol.submitOperation shouldBe empty
        humancontrol.retainedMaterials.map(_.fieldName) should contain("state")
        val human = _state(_view(scenario, humanresult))
        human.stage shouldBe "Human"
        human.receipts shouldBe acceptedwork.state.receipts
        human.commit shouldBe acceptedwork.state.commit
        human.delivery shouldBe acceptedwork.state.delivery
        human.automaticCount shouldBe acceptedwork.state.automaticCount
        human.ordinaryCount shouldBe acceptedwork.state.ordinaryCount
        human.conditionReport.forall(_.satisfied) shouldBe false
        _state(_advance_(destination.sessionId)) shouldBe human
        Files.readString(root.resolve("sbt-executions.txt")) shouldBe checks
        Files.readString(root.resolve("publication-count.txt")) shouldBe publications
        scenario.candidateHead shouldBe acceptedwork.state.commit.get.identity
        scenario.candidateStatus shouldBe ""
      } else {
      When("the exact DeliveryAcceptance result evaluates every obligation against those retained actual effects")
      val submittedacceptance = _submit_(current, finalresult, "delivery-acceptance")
      withClue(submittedacceptance.invocation.logs) { submittedacceptance.exitCode shouldBe 0 }
      val returned = _advance_(destination.sessionId)
      val returnedstate = _state(returned)
      val received = _observe_(requester.sessionId, "result-received", destination.sessionId, returned.state)
      val observed = _observe_(destination.sessionId, "return-observed", destination.sessionId)
      Then("actual result transport retains the same request timing and source with no duplicate checks commit or publication")
      returned.kind shouldBe "Return"
      returnedstate.conditionReport.forall(_.satisfied) shouldBe true
      _state(received).stage shouldBe "ResultReceived"
      _state(observed).stage shouldBe "Returned"
      _state(received).input.submissionConditions shouldBe Some(Vector.empty)
      _state(_advance_(requester.sessionId)) shouldBe _state(received)
      _state(_advance_(destination.sessionId)) shouldBe _state(observed)
      _state(_observe_(requester.sessionId, "result-received", destination.sessionId, returned.state)) shouldBe _state(received)
      Files.readString(root.resolve("sbt-executions.txt")) shouldBe checks
      Files.readString(root.resolve("publication-count.txt")) shouldBe publications
      scenario.candidateHead shouldBe acceptedwork.state.commit.get.identity
      scenario.candidateStatus shouldBe ""
      }
      info("SPECIFICATION-OWNED installed common delegation results retained under " + root)
  }
}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeCommonDelegateTaskCliSpec extends AnyWordSpec with NativeCommonDelegateTaskQualification {
  override protected lazy val commonDelegateExecutable: Path = {
    val path = Path.of(sys.props.get("smworkflow.spec.installedCommand")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select smworkflow.spec.installedCommand or SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path) && Files.isExecutable(path),
      "Select the actual absolute installed executable")
    path
  }
  "Installed common delegation" should {
    "collect the actual retained full-Test result through the selected candidate CAR without replay" in {
      val component = commonDelegateComponentFile.getOrElse(fail(
        "Select the native-published candidate CAR through SM_WORKFLOW_SPEC_COMPONENT_FILE or smworkflow.spec.componentFile"))
      new DelegateTaskValidationRecoveryOperationSpec().qualifyInstalledAvailableRecovery(commonDelegateExecutable, component)
    }
    Vector("accept", "decision-needed").foreach { outcome =>
      s"defer initial obligations then retain actual focused checks commit publication and $outcome through normal owners" in {
        qualifyCommonDelegate(outcome)
      }
    }
  }
}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskDeliveryCaptureCliSpec extends AnyWordSpec with Matchers {
  "Installed referenced DelegateTask validation" should {
    "roundtrip the actual managed Test capture through public requester operations" in {
      val executable = Path.of(sys.props.get("smworkflow.spec.installedCommand")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
      val component = Path.of(sys.props.get("smworkflow.spec.componentFile")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_COMPONENT_FILE")))
      new DelegateTaskValidationRecoveryOperationSpec().qualifyReturnedCapture(Some(executable -> component))
    }
  }
}

/** Explicit, independently selectable installed recovery qualification. */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskValidationRecoveryReviewCliSpec extends AnyWordSpec with NativeCommonDelegateTaskQualification {
  override protected lazy val commonDelegateExecutable: Path = {
    val path = Path.of(sys.props.get("smworkflow.spec.installedCommand")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select smworkflow.spec.installedCommand or SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path) && Files.isExecutable(path),
      "Select the actual absolute installed executable")
    path
  }
  "Installed recovery review" should {
    "collect the actual retained full-Test result through the selected candidate CAR without replay" in {
      val component = commonDelegateComponentFile.getOrElse(fail(
        "Select the native-published candidate CAR through SM_WORKFLOW_SPEC_COMPONENT_FILE or smworkflow.spec.componentFile"))
      new DelegateTaskValidationRecoveryOperationSpec().qualifyInstalledAvailableRecovery(commonDelegateExecutable, component)
    }
  }
}

/** Explicit OLD -> NEW qualification of one physical store. Fixture evidence is
  * specification-owned admission, never an answer on any consumer Session.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskTestOnlyRecoveryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parser.parse(Files.readString(path)).toOption.get
  private def _state(record: org.goldenport.record.Record): S.State =
    GoalPhaseNativeMetadata.normalizePublicJson(parser.parse(take(record.toJsonStringC)).toOption.get).toOption.get.as[S.State].toOption.get
  private def _installed: Path = {
    val selected = Path.of(sys.props.get("smworkflow.spec.installedCommand").getOrElse(fail("Select smworkflow.spec.installedCommand")))
    require(selected.isAbsolute && selected.normalize == selected && Files.isExecutable(selected))
    selected
  }
  private def _component: Path = {
    val selected = Path.of(sys.props.get("smworkflow.spec.componentFile").getOrElse(fail("Select smworkflow.spec.componentFile")))
    require(selected.isAbsolute && selected.normalize == selected && Files.isRegularFile(selected))
    selected
  }
  private def _call(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    scenario.callInstalled(_installed, service, operation, arguments,
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")),
      wait = Some(F.InstalledCallWait(600000L, 5000L)))
  private def _view(scenario: F.Scenario, result: F.Result): V.DelegateTaskView =
    NativeCommonDelegateTaskFixture.view(scenario, result, _call)
  private def _snapshot(scenario: F.Scenario, componentinstance: String, session: String, revision: Long): (V.WorkflowRetainedSnapshotReference, F.Result) = {
    val definition = take(DelegateTaskRuntimeCatalog.loadC())
    val reference = WorkflowRetainedSnapshot.publicReference(WorkflowRetainedSnapshot.Reference(WorkflowRetainedSnapshot.schema,
      componentinstance, "DelegateTaskWorkflow", definition.workflowRevision.value, session,
      session + ":standalone-state:" + revision, "1", revision.toString, "bounded-state", "json"))
    reference -> _call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedSnapshot.readOperation, Vector("--input", _json(reference)))
  }
  private def _select_import(scenario: F.Scenario, preparation: F.Result): Unit = scenario.selectPreparedImport(preparation)
  private def _prepare_direction(scenario: F.Scenario, fixture: Path, description: V.DelegateTaskValidationRecoveryView,
    proof: DelegateTaskValidationRecovery.Proof): V.DelegateTaskValidationRecoveryInput = {
    import DelegateTaskValidationRecovery.given
    val target = take(DelegateTaskValidationRecovery.targetC(description.target))
    val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-TestOnly-Human-" + target.frontierRevision, "1")
    val answer = V.DelegateTaskValidationRecoveryInput(target.sessionId, description.target, proof.asJson.noSpaces,
      "participant", reference)
    def _write_(name: String, value: RecordPresentable): String = {
      val file = fixture.resolve(name + "-" + target.frontierRevision + ".json")
      Files.writeString(file, _json(value))
      file.toUri.toASCIIString
    }
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None,
      Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "participant", "ordinary")))
    val input = V.WorkflowEvidencePreparationInput("DelegateTask", "delegate-task-validation-recovery",
      _write_("description", description), _write_("SPECIFICATION-OWNED-Human-answer", answer),
      _write_("reference", reference), _write_("execution", execution), "normal-development-host",
      java.time.Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
    // The actual normal preparation and importer admission are part of this
    // explicitly selected test fixture. No live consumer authority is claimed.
    val prepared = _call(scenario, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input)))
    _select_import(scenario, prepared)
    answer
  }
  private def _before(fixture: Path): Unit = {
    Given("the parent-selected OLD published CAR and a small real isolated ordinary native host")
    val manifestfile = fixture.resolve("fixture.json")
    require(!Files.exists(manifestfile), "BEFORE is create-only; preserve an already reached fixture and select collect")
    val initial = F.fresh("test-only-recovery-old-new", bootstrapSelectedInput = false,
      sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
    val root = initial.root.toRealPath()
    DelegateTaskTestOnlyRecoveryFixture.build(root)
    take(_run_setup_command_(Vector("-C", root.resolve("repository").toString, "add", "--", "build.sbt", "project/build.properties", ".gitignore")))
    take(_run_setup_command_(Vector("-C", root.resolve("repository").toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
      "commit", "-m", "Configure actual tiny TestOnly recovery build")))
    val host = initial.host.copy(goal = initial.host.goal.copy(candidatePaths = Vector("value.txt"),
      stepValidation = initial.host.goal.stepValidation.copy(checks = DelegateTaskTestOnlyRecoveryFixture.checks)))
    val configfile = root.resolve(".textus/config.json")
    val config = _read(configfile)
    val git = parser.parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val process = DelegateTaskTestOnlyRecoveryFixture.processPlan(root, git)
    Files.writeString(configfile, config.mapObject(_.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", process.noSpaces.asJson)
      .add("workflow.mechanical-receipt.max-bytes", "1048576".asJson)
      .add("textus.resource.url.file.roots", (root.toString + java.io.File.pathSeparator + fixture.toString).asJson)
      .add("textus.component.file", _component.toString.asJson)).noSpaces)
    initial.enablePreparedImportRetention()
    val scenario = F.reopenInstalledRepository(root, host, initial.plan, 100)
    var manifest = Json.obj("schemaVersion" -> "SPEC-TestOnly-old-new-v1".asJson, "root" -> root.toString.asJson,
      "database" -> _read(configfile).hcursor.get[String]("textus.datastore.path").toOption.get.asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(host), "plan" -> initial.plan.asJson,
      "oldComponent" -> _component.toString.asJson,
      "fixtureAuthority" -> "SPECIFICATION-OWNED; no actual consumer Human answer".asJson)
    def _save_(): Unit = Files.writeString(manifestfile, manifest.noSpaces)
    _save_()
    val input = V.DelegateTaskInput(host.goal.input.repositoryIdentity, "SPEC-TestOnly-old-new", "SPECIFICATION-OWNED same physical native recovery",
      NonEmptyVector.one("value.txt"), NonEmptyVector.of("compile", "fix-check", "test-only"), NonEmptyVector.one("behavior"),
      NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-original-source", "1")), true, "code", "off", 10, "direct-instruction", Vector.empty,
      V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component", "destination", "localized",
        NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector.empty,
        NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-request-authority", "1")), None, Vector.empty, "SPEC-only"), Vector("behavior"))
    val inputfile = root.resolve("SPEC-start-input.json")
    Files.writeString(inputfile, _json(input))
    val preparation = V.WorkflowDelegateTaskStartPreparationInput(inputfile.toUri.toASCIIString,
      "SPEC-TestOnly-selection", "participant", "ExplicitHuman", "SPEC-TestOnly-key", "entity:smworkflow.cli.goal_selection")
    _select_import(scenario, _call(scenario, "NativeWorkflowPreparationService", "prepareDelegateTaskStart", Vector("--input", _json(preparation))))
    val started = _view(scenario, _call(scenario, "DelegateTaskService", "startDelegateTask", Vector("--input", _json(input),
      "--selectionId", "SPEC-TestOnly-selection", "--participant", "participant", "--idempotencyKey", "SPEC-TestOnly-key")))
    manifest = manifest.mapObject(_.add("sessionId", started.sessionId.asJson)); _save_()
    val issuedraw = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", started.sessionId))
    val control = issuedraw.business[V.DelegateTaskView]
    val implement = _view(scenario, issuedraw)
    val componentinstance = control.retainedWork.map(_.componentInstance)
      .orElse(control.retainedMaterials.headOption.map(_.reference.componentInstance)).getOrElse(fail("Issued work must retain its owning Component reference"))
    manifest = manifest.mapObject(_.add("componentInstance", componentinstance.asJson)); _save_()
    Files.writeString(root.resolve("repository/value.txt"), "expected\n")
    val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-implementation", "1")
    val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED-implementer"), None,
      Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPECIFICATION-OWNED-implementer", "ordinary")))
    val result = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED actual selected fixture input", Vector.empty,
      NonEmptyVector.one(V.DelegateTaskCondition("behavior", true, "SPEC selected input")), Vector.empty, None, None)
    val submitted = _call(scenario, "DelegateTaskService", "submitDelegateTaskWorkResult", Vector("--sessionId", started.sessionId,
      "--issuedWorkContext", _json(implement.issuedWorkContext.get), "--result", _json(result), "--resultReference", _json(reference), "--executionEvidence", _json(evidence)))
    manifest = manifest.mapObject(_.add("acceptedSubmitLog", submitted.invocation.stdout.toString.asJson)); _save_()
    submitted.exitCode shouldBe 0
    When("OLD ordinary Advance executes real Compile, FixCheck and bounded TestOnly before its inline save fails")
    val advanced = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", started.sessionId))
    manifest = manifest.mapObject(_.add("oldAdvanceStdout", advanced.invocation.stdout.toString.asJson)
      .add("oldAdvanceStderr", advanced.invocation.stderr.toString.asJson).add("oldCallerExit", advanced.exitCode.asJson)); _save_()
    val described = _call(scenario, "WorkflowForceClosureService", "describeWorkflowForceClosure",
      Vector("--input", _json(V.WorkflowForceClosureDescriptionInput("DelegateTask", started.sessionId))))
    manifest = manifest.mapObject(_.add("oldFrontierDescriptionLog", described.invocation.stdout.toString.asJson)); _save_()
    val frontier = take(WorkflowForceClosure.targetC(described.business[V.WorkflowForceClosureView].target))
    frontier.family shouldBe "DelegateTask"
    frontier.sessionId shouldBe started.sessionId
    frontier.componentInstance shouldBe componentinstance
    val definition = take(DelegateTaskRuntimeCatalog.loadC())
    val catalog = take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.catalogC(Vector(definition)))
    val native = take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.decodeC(frontier.frontier.noSpaces, catalog))
    native.identity.value shouldBe started.sessionId
    native.definition shouldBe definition
    val (snapshot, read) = _snapshot(scenario, frontier.componentInstance, frontier.sessionId, native.revision.value)
    val state = _state(read.business[V.WorkflowRetainedSnapshotReadResult].material)
    state.stage shouldBe "ValidationIntent"
    state.pendingCheck shouldBe Some("test-only")
    manifest = manifest.mapObject(_.add("sourceReference", parser.parse(_json(snapshot)).toOption.get)
      .add("sourceState", state.asJson).add("beforeCommands", Files.readString(root.resolve("sbt-command-lines.txt")).asJson)
      .add("beforeBodyCount", Files.readAllLines(root.resolve("selected-body-executions.txt")).size().asJson)
      .add("readyForAfter", Json.True)); _save_()
    Then("the same physical Session retains actual successful prefix receipts and the old terminal/save evidence")
    advanced.exitCode should not be 0
    val logs = Files.readString(advanced.invocation.stdout) + Files.readString(advanced.invocation.stderr)
    logs should include("Mechanical receipt encoding")
    logs should include("OutputLimitExceeded")
    state.receipts.map(_.identity) shouldBe Vector("compile", "fix-check")
    state.receipts.zip(DelegateTaskTestOnlyRecoveryFixture.checks).foreach { (receipt, check) =>
      receipt.processObservation should not be empty
      RepositorySyncNativeMergeValidation.productSuccessful(receipt, check.kind) shouldBe true
    }
    Files.readAllLines(root.resolve("fix-check-executions.txt")).size() shouldBe 1
  }
  private def _after_or_collect(fixture: Path, collect: Boolean): Unit = {
    import DelegateTaskValidationRecovery.given
    Given("the exact retained OLD physical store and Session, reopened through the selected installed CAR")
    val manifestfile = fixture.resolve("fixture.json")
    var manifest = _read(manifestfile)
    val root = Path.of(manifest.hcursor.get[String]("root").toOption.get).toRealPath()
    val session = manifest.hcursor.get[String]("sessionId").toOption.get
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(manifest.hcursor.get[Json]("host").toOption.get))
    val plan = manifest.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get
    val configfile = root.resolve(".textus/config.json")
    val original = _read(configfile)
    original.hcursor.get[String]("textus.datastore.path").toOption.get shouldBe manifest.hcursor.get[String]("database").toOption.get
    Files.writeString(configfile, original.mapObject(_.add("textus.component.file", _component.toString.asJson)
      .add("workflow.mechanical-receipt.max-bytes", "67108864".asJson)).noSpaces)
    val logroot = root.resolve("logs")
    val files = Files.list(logroot)
    val nextindex = try { import scala.jdk.CollectionConverters.*; files.iterator().asScala.map(_.getFileName.toString.takeWhile(_.isDigit)).filter(_.nonEmpty).map(_.toInt).max + 100 }
      finally files.close()
    val scenario = F.reopenInstalledRepository(root, host, plan, nextindex)
    val commands = Files.readString(root.resolve("sbt-command-lines.txt"))
    When("the exact reached Session's recovery description is requested through the installed CAR")
    val descriptionresult = _call(scenario, "DelegateTaskService", "describeDelegateTaskValidationRecovery",
      Vector("--input", _json(V.DelegateTaskValidationRecoveryDescriptionInput(session))))
    manifest = manifest.mapObject(_.add("latestDescriptionLog", descriptionresult.invocation.stdout.toString.asJson))
    Files.writeString(manifestfile, manifest.noSpaces)
    val description = descriptionresult.business[V.DelegateTaskValidationRecoveryView]
    val target = take(DelegateTaskValidationRecovery.targetC(description.target))
    Then("the decoded target retains selected TestOnly and its successful actual prefix")
    target.check.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
    target.successfulPrefix shouldBe Vector("compile", "fix-check")
    if (collect && target.retainedOutcome.contains("incomplete")) {
      Then("the reached genuine Incomplete is exposed without admitting another attempt")
      take(DelegateTaskValidationRecovery.targetC(description.target)).retainedOutcome shouldBe Some("incomplete")
    } else {
      val available = target.captureAvailability == "available"
      require(!collect || available, "Collection requires a genuine available native artifact; preserve this exact pending state")
      require(collect || !manifest.hcursor.get[Boolean]("newAttemptEntered").toOption.contains(true),
        "An actual new attempt was already entered; use collect on this same fixture")
      val proof = if (available) {
        val artifacts = Files.walk(root.resolve("capture-root"))
        val captures = try { import scala.jdk.CollectionConverters.*; artifacts.iterator().asScala.filter(Files.isRegularFile(_)).filter(_.toString.endsWith(".json"))
          .map(_read).filter(json => json.hcursor.downField("association").get[String]("session").contains(session) &&
            json.hcursor.downField("association").get[Long]("intentRevision").contains(target.frontierRevision)).toVector }
          finally artifacts.close()
        captures.size shouldBe 1
        val process = WorkflowSbtCapture.decode(captures.head.hcursor.get[Json]("process").toOption.get).toOption.get
        val facts = captures.head.hcursor.get[WorkflowSbtValidation.Facts]("facts").toOption.get
        WorkflowSbtCapture.structurallyValid(process, facts) shouldBe true
        DelegateTaskValidationRecovery.Proof("SPECIFICATION-OWNED-authentic-native-artifact", "SPECIFICATION-OWNED-ended-caller",
          WorkflowSbtCapture.termination(process), WorkflowSbtCapture.exitCode(process), "completed", 0, "host-attested", "available", "unknown")
      } else {
        target.captureAvailability shouldBe "not-retained"
        val stderr = Path.of(manifest.hcursor.get[String]("oldAdvanceStderr").toOption.get)
        val stdout = Path.of(manifest.hcursor.get[String]("oldAdvanceStdout").toOption.get)
        (Files.readString(stderr) + Files.readString(stdout)) should include("OutputLimitExceeded")
        DelegateTaskValidationRecovery.Proof(stderr.toUri.toASCIIString, stdout.toUri.toASCIIString, "output-limit-exceeded", None,
          "failed", manifest.hcursor.get[Int]("oldCallerExit").toOption.get, "host-attested", "unavailable", "unknown")
      }
      When("the fixture prepares and admits its exact specification-owned Human direction through ordinary public operations")
      val answer = _prepare_direction(scenario, fixture, description, proof)
      val recovery = _call(scenario, "DelegateTaskService", "recoverDelegateTaskValidation", Vector("--input", _json(answer)))
      manifest = manifest.mapObject(_.add("latestRecoveryLog", recovery.invocation.stdout.toString.asJson))
      Files.writeString(manifestfile, manifest.noSpaces)
      Then("recovery collects the actual result or records the direction without executing a check")
      recovery.business[V.DelegateTaskValidationRecoveryView].outcome shouldBe (if (available) "actual-result-collected" else "authorized-awaiting-advance")
      Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe commands
      When("the same exact recovery direction is submitted again")
      val repeated = _call(scenario, "DelegateTaskService", "recoverDelegateTaskValidation", Vector("--input", _json(answer)))
      Then("the repeated direction is refused")
      repeated.exitCode should not be 0
      if (!available && !collect) {
        manifest = manifest.mapObject(_.add("newAttemptEntered", Json.True)); Files.writeString(manifestfile, manifest.noSpaces)
        When("a separate ordinary Advance consumes the admitted remaining-check attempt")
        val advanced = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", session))
        manifest = manifest.mapObject(_.add("newAdvanceStdout", advanced.invocation.stdout.toString.asJson).add("newAdvanceStderr", advanced.invocation.stderr.toString.asJson))
        Files.writeString(manifestfile, manifest.noSpaces)
        val result = _view(scenario, advanced)
        Then("the genuine selected output-limit result is exposed as Wait")
        result.kind shouldBe "Wait"
        val state = _state(result.state.get)
        val old = manifest.hcursor.get[S.State]("sourceState").toOption.get
        Then("one remaining real TestOnly is Incomplete and the actual prefix and cumulative accounting survive unchanged")
        state.receipts.take(2) shouldBe old.receipts
        state.automaticCount shouldBe old.automaticCount
        state.ordinaryCount shouldBe old.ordinaryCount
        state.candidate shouldBe old.candidate
        state.conditionReport shouldBe old.conditionReport
        state.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
        state.receipts.last.externalCapture should not be empty
        val capture = state.receipts.last.externalCapture.get
        val native = WorkflowSbtCapture.decode(_read(root.resolve("capture-root").resolve(capture.path))
          .hcursor.get[Json]("process").toOption.get).toOption.get
        native.stdout.truncated shouldBe true
        WorkflowSbtCapture.externalMatches(capture, state.receipts.last.sbt.get, native) shouldBe true
        Files.readAllLines(root.resolve("selected-body-executions.txt")).size() shouldBe manifest.hcursor.get[Int]("beforeBodyCount").toOption.get + 1
        Files.readAllLines(root.resolve("fix-check-executions.txt")).size() shouldBe 1
        val aftercommands = Files.readString(root.resolve("sbt-command-lines.txt"))
        aftercommands.linesIterator.count(_ == "compile") shouldBe commands.linesIterator.count(_ == "compile")
        When("ordinary Advance is repeated at the newly recorded Incomplete frontier")
        val repeatedadvance = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", session))
        Then("the repeated Advance returns successfully without executing another check")
        repeatedadvance.exitCode shouldBe 0
        Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe aftercommands
      }
    }
    if (collect) Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe commands
  }
  "Installed common TestOnly recovery" should {
    "qualify only the explicitly selected before after or collect stage on the same physical Session" in {
      val phase = sys.props.get("smworkflow.spec.testOnlyRecoveryPhase").getOrElse(fail("Select before|after|collect"))
      val fixture = Path.of(sys.props.get("smworkflow.spec.testOnlyRecoveryFixture").getOrElse(fail("Select the parent-owned absolute ignored fixture directory")))
      require(fixture.isAbsolute && fixture.normalize == fixture && fixture.startsWith(Path.of("target").toAbsolutePath.normalize),
        "Fixture evidence must remain under the absolute ignored repository target")
      Files.createDirectories(fixture)
      phase match {
        case "before" => _before(fixture)
        case "after" => _after_or_collect(fixture, false)
        case "collect" => _after_or_collect(fixture, true)
        case _ => fail("Select exactly before|after|collect")
      }
    }
  }
}

/** Focused installed continuation only; no unrelated common-delegation scenarios. */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskRepairContinuationCliSpec extends AnyWordSpec with Matchers {
  "Installed DelegateTask repair continuation" should {
    "describe, admit and separately advance the same specification-owned stop" in {
      val executable = Path.of(sys.props.get("smworkflow.spec.installedCommand")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
      val component = Path.of(sys.props.get("smworkflow.spec.componentFile")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_COMPONENT_FILE")))
      new DelegateTaskRepairContinuationOperationSpec().qualify(Some(executable -> component))
    }
  }
}

/** Installed semantic continuation qualification on a specification-owned Session. */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskSemanticContinuationCliSpec extends AnyWordSpec with Matchers {
  "Installed DelegateTask semantic continuation" should {
    "admit and separately advance the original semantic stop after reopen" in {
      val executable = Path.of(sys.props.get("smworkflow.spec.installedCommand")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
      val component = Path.of(sys.props.get("smworkflow.spec.componentFile")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_COMPONENT_FILE")))
      new DelegateTaskSemanticContinuationOperationSpec().qualify(Some(executable -> component))
    }
  }
}

/** Two explicitly selected phases reopen one isolated native Session across
  * normal installed publication; fixture facts never stand in for execution.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskRepeatedSemanticContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Installed repeated DelegateTask semantic continuation" should {
    "retain, continue or collect the same actual Repair across installed publication" in {
      Given("an explicitly selected before|after|collect-after phase and dedicated absolute fixture path")
      val phase = sys.props.getOrElse("smworkflow.spec.semanticContinuationPhase", fail("Select semanticContinuationPhase=before|after|collect-after"))
      val fixture = Path.of(sys.props.getOrElse("smworkflow.spec.semanticContinuationFixture", fail("Select semanticContinuationFixture")))
      Set("before", "after", "collect-after") should contain (phase)
      fixture.isAbsolute shouldBe true
      val executable = Path.of(sys.props.get("smworkflow.spec.installedCommand")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
      val component = Path.of(sys.props.get("smworkflow.spec.componentFile")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_COMPONENT_FILE")))
      When("the selected ordinary installed runtime uses that same fixture")
      new DelegateTaskSemanticContinuationOperationSpec().qualify(Some(executable -> component), Some(phase -> fixture))
      Then("the actual Session references remain recorded in that same dedicated fixture")
      Files.isRegularFile(fixture.resolve("SPEC-repeated-semantic-fixture.json")) shouldBe true
    }
  }
}

/** Installed Review continuation qualification on a specification-owned Session. */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskReviewContinuationCliSpec extends AnyWordSpec with Matchers {
  "Installed DelegateTask Review continuation" should {
    "admit and separately advance the original Review stop after reopen" in {
      val executable = Path.of(sys.props.get("smworkflow.spec.installedCommand")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
      val component = Path.of(sys.props.get("smworkflow.spec.componentFile")
        .orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE"))
        .getOrElse(fail("Select SM_WORKFLOW_SPEC_COMPONENT_FILE")))
      val specimen = new DelegateTaskReviewContinuationOperationSpec()
      val small = specimen.qualify(Some(executable -> component))
      val large = specimen.qualify(Some(executable -> component), large = true)
      small.keys shouldBe large.keys
      small.foreach { case (key, size) => withClue(key) { math.abs(large(key) - size) should be <= 256 } }
    }
  }
}
