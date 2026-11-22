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
  * @version Nov. 20, 2026
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
      val repairmode = Files.exists(root.resolve("repair-before-retry-enabled"))
      When("the actual selected body observes the fixture input")
      Files.writeString(root.resolve("selected-body-executions.txt"), java.lang.ProcessHandle.current().pid().toString + "\n",
        StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      val actual = Files.readString(repository.resolve("value.txt"))
      if (if (repairmode) actual.linesIterator.contains("verbose") else Files.exists(root.resolve("flood-enabled"))) {
        System.out.print("D" * (2 * 1024 * 1024))
        System.out.flush()
      }
      Then("the actual product input remains selected and successful when its report is complete")
      if (repairmode) actual.linesIterator.next() shouldBe "expected" else actual shouldBe "expected\n"
    }
  }
}

/** Inputs for the common native pipeline, with no manufactured process receipt.
  * @since   Nov. 19, 2026
  * @version Nov. 20, 2026
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
      |  val actual = IO.read(baseDirectory.value / "value.txt")
      |  val repairmode = (baseDirectory.value.getParentFile / "repair-before-retry-enabled").isFile
      |  if (if (repairmode) !actual.startsWith("expected\n") else actual != "expected\n") sys.error("SPEC actual input differs")
      |}
      |""".stripMargin, StandardOpenOption.APPEND)
    Files.writeString(root.resolve("flood-enabled"), "SPECIFICATION-OWNED bounded 2 MiB output producer\n")
    Files.createDirectories(root.resolve("capture-root"))
  }
  def completionPlan(root: Path, git: Json): Json = {
    import org.goldenport.cncf.processexecution.GitOperationKind
    def _array_(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
    def _key_(program: Json): Vector[Json] = Vector("target", "operation", "selectionId").map(name =>
      program.hcursor.get[Json](name).getOrElse(Json.Null))
    val target = org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
      root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString)
    val generated = TypedGitHostPlanFixture.plan(target, kinds = Vector(GitOperationKind.CommitMetadata),
      goalMessages = Vector("SPEC complete the reviewed recorded Incomplete repair"),
      goalSelections = Vector(Vector("value.txt")))
    val required = _array_(generated, "gitPrograms").map { program =>
      if (!program.hcursor.get[String]("selectionId").contains("goal-step-1")) program else {
        val operation = GitOperationKind.values.find(_.wireName == program.hcursor.get[String]("operation").toOption.get).get
        val identity = TypedGitHostPlanFixture.capability(operation, Some("delegate-task"))
        program.mapObject(_.add("selectionId", "delegate-task".asJson)
          .add("identity", identity.asJson).add("capability", identity.asJson))
      }
    }
    val existing = _array_(git, "gitPrograms")
    val programs = existing ++ required.filterNot(program => existing.exists(old => _key_(old) == _key_(program)))
    val keys = required.map(_key_).toSet
    val capabilities = programs.filter(program => keys(_key_(program)))
      .map(_.hcursor.get[Json]("capability").toOption.get).distinct
    val grants = _array_(git, "grants")
    val owner = SmWorkflowComponent.componentId.name
    val admitted = if (grants.exists(_.hcursor.get[String]("componentId").contains(owner))) grants.map { grant =>
      if (!grant.hcursor.get[String]("componentId").contains(owner)) grant
      else grant.mapObject(_.add("capabilities", (_array_(grant, "capabilities") ++ capabilities).distinct.asJson))
    } else grants :+ Json.obj("componentId" -> owner.asJson, "capabilities" -> capabilities.asJson)
    git.mapObject(_.add("gitPrograms", programs.asJson).add("grants", admitted.asJson))
  }
  def processPlan(root: Path, git: Json): Json = {
    def _array_(value: Json, name: String) = value.hcursor.get[Vector[Json]](name).toOption.get
    val completion = completionPlan(root, git)
    // Reuse typed Git authority only; replace the old fixture's SBT workspaces
    // together with its programs so each ordinal remains uniquely declared.
    val oldtargets = _array_(completion, "sbtPrograms").flatMap(_.hcursor.get[String]("target").toOption).toSet
    val gitonly = completion.mapObject(_.add("sbtPrograms", Json.arr())
      .add("workspaces", Json.fromValues(_array_(completion, "workspaces").filterNot(_.hcursor.get[String]("identity").toOption.exists(oldtargets))))
      .add("workspaceGrants", Json.fromValues(_array_(completion, "workspaceGrants").filterNot(_.hcursor.get[String]("workspaceId").toOption.exists(oldtargets)))))
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
          "SPECIFICATION-OWNED artifact return; consumer selection and activation remain separate", None, None, None), Vector.empty)
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
            Some(destination), result, None, "SPECIFICATION-OWNED transport observation; no consumer effect is certified", None)))))
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

/** Shared qualification helpers run on the currently executing suite instance.
  * Fixture evidence never supplies an answer on a consumer Session.
  * @since   Nov. 19, 2026
  * @version Nov. 20, 2026
  */
private[admission] trait NativeDelegateTaskTestOnlyQualification extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parser.parse(Files.readString(path)).toOption.get
  private def _state(record: org.goldenport.record.Record): S.State = {
    val parsed = parser.parse(take(record.toJsonStringC)).fold(error =>
      fail("SPEC retained DelegateTask state JSON: " + error.message), identity)
    val normalized = GoalPhaseNativeMetadata.normalizePublicJson(parsed).fold(error =>
      fail("SPEC retained DelegateTask state naming normalization: " + error), identity)
    normalized.as[S.State].fold(error =>
      fail("SPEC retained DelegateTask state decoder at " + error.history.mkString("/") + ": " + error.message), identity)
  }
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
  private def _qualify_recorded_wait(scenario: F.Scenario, manifest: Json, state: S.State): Unit = {
    val root = scenario.root
    val old = manifest.hcursor.get[S.State]("sourceState").fold(error =>
      fail("SPEC fixture sourceState decoder at " + error.history.mkString("/") + ": " + error.message), identity)
    Then("one remaining real TestOnly is Incomplete and the actual prefix and cumulative accounting survive unchanged")
    state.stage shouldBe "Wait"
    state.receipts.take(2) shouldBe old.receipts
    state.automaticCount shouldBe old.automaticCount
    state.ordinaryCount shouldBe old.ordinaryCount
    state.candidate shouldBe old.candidate
    state.conditionReport shouldBe old.conditionReport
    state.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
    state.receipts.last.externalCapture should not be empty
    val capture = state.receipts.last.externalCapture.get
    val capturepath = root.resolve("capture-root").resolve(capture.path)
    val processjson = _read(capturepath).hcursor.get[Json]("process").fold(error =>
      fail("SPEC native capture process at " + capturepath + ": " + error.message), identity)
    val native = WorkflowSbtCapture.decode(processjson).fold(error =>
      fail("SPEC native capture decoder at " + capturepath + ": " + error), identity)
    native.stdout.truncated shouldBe true
    WorkflowSbtCapture.externalMatches(capture, state.receipts.last.sbt.get, native) shouldBe true
    val bodycount = Files.readAllLines(root.resolve("selected-body-executions.txt")).size()
    bodycount shouldBe manifest.hcursor.get[Int]("beforeBodyCount").toOption.get + 1
    Files.readAllLines(root.resolve("fix-check-executions.txt")).size() shouldBe 1
    val commands = Files.readString(root.resolve("sbt-command-lines.txt"))
    val originalcommands = manifest.hcursor.get[String]("beforeCommands").toOption.get
    commands.linesIterator.count(_ == "compile") shouldBe originalcommands.linesIterator.count(_ == "compile")
    When("ordinary Advance is repeated once at the genuine recorded Incomplete frontier")
    val repeatedadvance = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", manifest.hcursor.get[String]("sessionId").toOption.get))
    Then("the passive Advance exposes Wait without executing another check or selected body")
    repeatedadvance.exitCode shouldBe 0
    repeatedadvance.business[V.DelegateTaskView].kind shouldBe "Wait"
    Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe commands
    Files.readAllLines(root.resolve("selected-body-executions.txt")).size() shouldBe bodycount
  }
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
  private def _before(fixture: Path, repair: Boolean = false): Unit = {
    Given("the parent-selected OLD published CAR and a small real isolated ordinary native host")
    val manifestfile = fixture.resolve("fixture.json")
    val prior = Option.when(Files.exists(manifestfile))(_read(manifestfile))
    prior.foreach { saved =>
      require(saved.hcursor.downField("sessionId").focus.isEmpty && saved.hcursor.downField("readyForAfter").focus.isEmpty,
        "BEFORE cannot replay an accepted or reached Session; preserve this fixture and select after or collect")
      require(saved.hcursor.get[String]("schemaVersion").contains("SPEC-TestOnly-old-new-v1"), "Select the saved pre-Session fixture")
    }
    var manifest = prior.getOrElse {
      val initial = F.fresh("test-only-recovery-old-new", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
      val root = initial.root.toRealPath()
      DelegateTaskTestOnlyRecoveryFixture.build(root)
      if (repair) Files.writeString(root.resolve("repair-before-retry-enabled"), "SPEC source-controlled verbose output\n")
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
        .add("workflow.mechanical-receipt.max-bytes", (if (repair) "67108864" else "1048576").asJson)
        .add("textus.resource.url.file.roots", Vector(root, fixture).map(_.toString).mkString(",").asJson)
        .add("textus.component.file", _component.toString.asJson)).noSpaces)
      initial.enablePreparedImportRetention()
      Json.obj("schemaVersion" -> "SPEC-TestOnly-old-new-v1".asJson, "root" -> root.toString.asJson,
        "database" -> _read(configfile).hcursor.get[String]("textus.datastore.path").toOption.get.asJson,
        "host" -> ThreeWorkflowRuntimeParameters.encode(host), "plan" -> initial.plan.asJson,
        "oldComponent" -> _component.toString.asJson,
        "recordedIncompleteRepair" -> repair.asJson,
        "fixtureAuthority" -> "SPECIFICATION-OWNED; no actual consumer Human answer".asJson)
    }
    val root = Path.of(manifest.hcursor.get[String]("root").toOption.get).toRealPath()
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(manifest.hcursor.get[Json]("host").toOption.get))
    val plan = manifest.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get
    val configfile = root.resolve(".textus/config.json")
    val config = _read(configfile)
    require(root.toString == manifest.hcursor.get[String]("root").toOption.get &&
      host.target.root == root.resolve("repository").toRealPath().toString &&
      config.hcursor.get[String]("textus.datastore.path") == manifest.hcursor.get[String]("database") &&
      (prior.isEmpty || Files.isRegularFile(Path.of(manifest.hcursor.get[String]("database").toOption.get))) &&
      config.hcursor.get[String]("textus.component.file") == manifest.hcursor.get[String]("oldComponent") &&
      manifest.hcursor.get[String]("oldComponent").contains(_component.toString),
      "Pre-Session continuation requires the same physical root, database, saved host and selected OLD CAR")
    val logical = BoundedWorkExecutionAdmissionFixture.logicalParameters.foldLeft(config) { case (retained, (key, value)) =>
      retained.mapObject(_.add(key, value.asJson))
    }
    val corrected = logical.mapObject(_.add("textus.resource.url.file.roots", Vector(root, fixture).map(_.toString).mkString(",").asJson))
    if (corrected != config) Files.writeString(configfile, corrected.noSpaces)
    val logroot = root.resolve("logs")
    val nextindex = if (Files.isDirectory(logroot)) {
      val files = Files.list(logroot)
      try {
        import scala.jdk.CollectionConverters.*
        files.iterator().asScala.flatMap(_.getFileName.toString.takeWhile(_.isDigit).toIntOption).maxOption
          .map(index => Math.addExact(index, 100)).getOrElse(100)
      } finally files.close()
    } else 100
    val scenario = F.reopenInstalledRepository(root, host, plan, nextindex)
    def _save_(): Unit = Files.writeString(manifestfile, manifest.noSpaces)
    if (prior.isEmpty) _save_()
    val inputfile = root.resolve("SPEC-start-input.json")
    val input = if (prior.nonEmpty) {
      val retained = take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(inputfile)))
      take(V.DelegateTaskInput.createC(retained))
    } else V.DelegateTaskInput(host.goal.input.repositoryIdentity, "SPEC-TestOnly-old-new", "SPECIFICATION-OWNED same physical native recovery",
      NonEmptyVector.one("value.txt"), NonEmptyVector.of("compile", "fix-check", "test-only"), NonEmptyVector.one("behavior"),
      NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-original-source", "1")), true, "code", "off", 10, "direct-instruction", Vector.empty,
      V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component", "destination", "localized",
        NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector.empty,
        NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-request-authority", "1")), None, Vector.empty, "SPEC-only", None, None, None), Vector("behavior"))
    if (prior.isEmpty) Files.writeString(inputfile, _json(input))
    val preparation = V.WorkflowDelegateTaskStartPreparationInput(inputfile.toUri.toASCIIString,
      "SPEC-TestOnly-selection", "participant", "ExplicitHuman", "SPEC-TestOnly-key", "entity:smworkflow.cli.goal_selection")
    When("the unchanged unaccepted input is prepared through the ordinary public route")
    _select_import(scenario, _call(scenario, "NativeWorkflowPreparationService", "prepareDelegateTaskStart", Vector("--input", _json(preparation))))
    When("the first ordinary Start selects that same prepared input and original properties")
    val started = _view(scenario, _call(scenario, "DelegateTaskService", "startDelegateTask", Vector("--input", _json(input),
      "--selectionId", "SPEC-TestOnly-selection", "--participant", "participant", "--idempotencyKey", "SPEC-TestOnly-key")))
    manifest = manifest.mapObject(_.add("sessionId", started.sessionId.asJson)); _save_()
    val issuedraw = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", started.sessionId))
    val control = issuedraw.business[V.DelegateTaskView]
    val implement = _view(scenario, issuedraw)
    val componentinstance = control.retainedWork.map(_.componentInstance)
      .orElse(control.retainedMaterials.headOption.map(_.reference.componentInstance)).getOrElse(fail("Issued work must retain its owning Component reference"))
    manifest = manifest.mapObject(_.add("componentInstance", componentinstance.asJson)); _save_()
    Files.writeString(root.resolve("repository/value.txt"), if (repair) "expected\nverbose\n" else "expected\n")
    val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-implementation", "1")
    val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED-implementer"), None,
      Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPECIFICATION-OWNED-implementer", "ordinary")))
    val result = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED actual selected fixture input", Vector.empty,
      NonEmptyVector.one(V.DelegateTaskCondition("behavior", true, "SPEC selected input")), Vector.empty, None, None)
    val submitted = _call(scenario, "DelegateTaskService", "submitDelegateTaskWorkResult", Vector("--sessionId", started.sessionId,
      "--issuedWorkContext", _json(implement.issuedWorkContext.get), "--result", _json(result), "--resultReference", _json(reference), "--executionEvidence", _json(evidence)))
    manifest = manifest.mapObject(_.add("acceptedSubmitLog", submitted.invocation.stdout.toString.asJson)); _save_()
    submitted.exitCode shouldBe 0
    When("OLD ordinary Advance executes real Compile, FixCheck and bounded TestOnly")
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
    state.stage shouldBe (if (repair) "Wait" else "ValidationIntent")
    state.pendingCheck shouldBe (if (repair) None else Some("test-only"))
    manifest = manifest.mapObject(_.add("sourceReference", parser.parse(_json(snapshot)).toOption.get)
      .add("sourceState", state.asJson).add("beforeCommands", Files.readString(root.resolve("sbt-command-lines.txt")).asJson)
      .add("beforeBodyCount", Files.readAllLines(root.resolve("selected-body-executions.txt")).size().asJson)
      .add("readyForAfter", Json.True)); _save_()
    Then("the same physical Session retains actual successful prefix receipts and the old terminal evidence")
    val logs = Files.readString(advanced.invocation.stdout) + Files.readString(advanced.invocation.stderr)
    if (repair) {
      advanced.exitCode shouldBe 0
      state.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
      val capture = state.receipts.last.externalCapture.get
      val process = WorkflowSbtCapture.decode(_read(root.resolve("capture-root").resolve(capture.path))
        .hcursor.get[Json]("process").toOption.get).toOption.get
      process.termination shouldBe org.goldenport.cncf.processexecution.ProcessExecutionTermination.OutputLimitExceeded(
        org.goldenport.cncf.processexecution.ProcessExecutionStream.Stdout)
      WorkflowSbtCapture.externalMatches(capture, state.receipts.last.sbt.get, process) shouldBe true
      val refused = _call(scenario, "DelegateTaskService", "describeDelegateTaskRepairContinuation",
        Vector("--input", _json(V.GoalTaskRepairContinuationDescriptionInput(started.sessionId))))
      refused.exitCode should not be 0
      manifest = manifest.mapObject(_.add("oldRepairRefusalLog", refused.invocation.stdout.toString.asJson))
      _save_()
    } else {
      advanced.exitCode should not be 0
      logs should include("Mechanical receipt encoding")
      logs should include("OutputLimitExceeded")
    }
    state.receipts.take(2).map(_.identity) shouldBe Vector("compile", "fix-check")
    state.receipts.take(2).zip(DelegateTaskTestOnlyRecoveryFixture.checks).foreach { (receipt, check) =>
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
      target.sessionId shouldBe session
      target.componentInstance shouldBe manifest.hcursor.get[String]("componentInstance").toOption.get
      When("the exact actual stopped frontier is read through its public retained snapshot")
      val (snapshot, read) = _snapshot(scenario, target.componentInstance, target.sessionId, target.frontierRevision)
      manifest = manifest.mapObject(_.add("collectedWaitReference", parser.parse(_json(snapshot)).toOption.get)
        .add("collectedWaitSnapshotLog", read.invocation.stdout.toString.asJson))
      Files.writeString(manifestfile, manifest.noSpaces)
      val state = _state(read.business[V.WorkflowRetainedSnapshotReadResult].material)
      _qualify_recorded_wait(scenario, manifest, state)
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
        _qualify_recorded_wait(scenario, manifest, state)
      }
    }
    if (collect) Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe commands
  }
  protected def qualify_recorded_incomplete(phase: String, fixture: Path): Unit = {
    require(fixture.isAbsolute && fixture.normalize == fixture && fixture.startsWith(Path.of("target").toAbsolutePath.normalize),
      "Select one parent-owned ignored absolute fixture directory")
    Files.createDirectories(fixture)
    phase match {
      case "prepare-old" => _before(fixture, repair = true)
      case "resume-new" => _resume_recorded_repair(fixture)
      case "complete-new" => _complete_recorded_repair(fixture)
      case _ => fail("Select prepare-old|resume-new|complete-new")
    }
  }

  private def _resume_recorded_repair(fixture: Path): Unit = {
    import DelegateTaskRepairContinuation as R
    import BoundedWorkRuntime.given
    Given("the OLD genuine ended Incomplete and successful prefix in the same physical isolated Session")
    val manifestfile = fixture.resolve("fixture.json")
    var manifest = _read(manifestfile)
    require(manifest.hcursor.get[Boolean]("recordedIncompleteRepair").contains(true) &&
      !manifest.hcursor.get[Boolean]("repairDirectionEntered").contains(true),
      "Resume this exact prepared Session once; preserve any already entered direction")
    val root = Path.of(manifest.hcursor.get[String]("root").toOption.get).toRealPath()
    val session = manifest.hcursor.get[String]("sessionId").toOption.get
    val old = manifest.hcursor.get[S.State]("sourceState").toOption.get
    val oldreference = take(V.WorkflowRetainedSnapshotReference.createC(
      take(new org.goldenport.record.io.RecordDecoder().json(manifest.hcursor.get[Json]("sourceReference").toOption.get.noSpaces))))
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(manifest.hcursor.get[Json]("host").toOption.get))
    val plan = manifest.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get
    val configfile = root.resolve(".textus/config.json")
    val config = _read(configfile)
    config.hcursor.get[String]("textus.datastore.path") shouldBe manifest.hcursor.get[String]("database")
    Files.isRegularFile(Path.of(manifest.hcursor.get[String]("database").toOption.get)) shouldBe true
    Files.writeString(configfile, config.mapObject(_.add("textus.component.file", _component.toString.asJson)).noSpaces)
    val files = Files.list(root.resolve("logs"))
    val nextindex = try { import scala.jdk.CollectionConverters.*; files.iterator().asScala
      .flatMap(_.getFileName.toString.takeWhile(_.isDigit).toIntOption).max + 100 } finally files.close()
    val scenario = F.reopenInstalledRepository(root, host, plan, nextindex)
    val commands = Files.readString(root.resolve("sbt-command-lines.txt"))
    val capture = old.receipts.last.externalCapture.get
    val capturefile = root.resolve("capture-root").resolve(capture.path)
    val capturebytes = Files.readAllBytes(capturefile).toVector
    def _save_(): Unit = Files.writeString(manifestfile, manifest.noSpaces)
    def _advance_(): V.DelegateTaskView = _view(scenario,
      _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", session)))
    def _work_(view: V.DelegateTaskView): S.Work = GoalPhaseNativeMetadata.normalizePublicJson(
      parser.parse(take(view.work.get.toJsonStringC)).toOption.get).toOption.get.as[S.Work].toOption.get
    def _prepare_(kind: String, name: String, request: RecordPresentable, answer: RecordPresentable,
      reference: V.WorkflowArtifactReference, worker: String): V.WorkflowExecutionEvidence = {
      def _write_(suffix: String, value: RecordPresentable): String = {
        val file = fixture.resolve(name + "-" + suffix + ".json")
        Files.writeString(file, _json(value))
        file.toUri.toASCIIString
      }
      val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", worker, "ordinary")))
      val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", kind,
        _write_("request", request), _write_("answer", answer), _write_("reference", reference), _write_("execution", evidence),
        "normal-development-host", java.time.Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence",
        Option.when(kind == "repair-continuation")("participant"))
      _select_import(scenario, _call(scenario, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation))))
      evidence
    }
    def _submit_(view: V.DelegateTaskView, outcome: String, name: String, worker: String): F.Result = {
      val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + name, "1")
      val work = _work_(view)
      val required = if (work.kind == "DeliveryAcceptance") work.state.input.conditions.toSet
        else work.state.input.submissionConditions.getOrElse(work.state.input.conditions).toSet
      val report = work.state.input.conditions.map(identity => V.DelegateTaskCondition(identity, required(identity),
        if (required(identity)) "Actual scoped input and retained selected evidence"
        else "Later validation commit and delivery remain unevaluated by this submission"))
      val result = V.DelegateTaskWorkResult(outcome, "SPECIFICATION-OWNED " + name, Vector.empty,
        NonEmptyVector.fromVectorUnsafe(report),
        Vector.empty, None, None)
      val evidence = if (outcome == "accept" && work.kind == "Review")
        _prepare_("review", name, view, result, reference, worker)
        else V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None,
          Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", worker, "ordinary")))
      _call(scenario, "DelegateTaskService", "submitDelegateTaskWorkResult", Vector("--sessionId", session,
        "--issuedWorkContext", _json(view.issuedWorkContext.get), "--result", _json(result),
        "--resultReference", _json(reference), "--executionEvidence", _json(evidence)))
    }
    When("NEW describes the original Wait through its existing public repair operation")
    val describedresult = _call(scenario, "DelegateTaskService", "describeDelegateTaskRepairContinuation",
      Vector("--input", _json(V.GoalTaskRepairContinuationDescriptionInput(session))))
    val description = describedresult.business[V.GoalTaskRepairContinuationDescriptionResult]
    val target = take(R.targetC(description.target))
    Then("the new source retains the original candidate receipt prefix and cumulative accounting")
    target.source shouldBe Some(R.Source.RecordedIncomplete)
    description.choices shouldBe Vector("RepairBeforeRetry", "Defer")
    target.business.state.asJson shouldBe old.asJson
    target.sessionId shouldBe session
    target.componentInstance shouldBe manifest.hcursor.get[String]("componentInstance").toOption.get
    val human = V.WorkflowArtifactReference("SPECIFICATION-OWNED-repair-before-retry-human", "1")
    val answer = V.GoalTaskRepairContinuationInput(session, description.target, "RepairBeforeRetry",
      "SPEC remove source-selected verbose output while preserving the scalar expectation", "participant", human)
    _call(scenario, "DelegateTaskService", "resumeDelegateTaskRepair", Vector("--input", _json(answer))).exitCode should not be 0
    val defer = answer.copy(choice = "Defer", reference = human.copy(identity = human.identity + "-defer"))
    When("independent specification-owned Human evidence selects Defer")
    _prepare_("repair-continuation", "defer", description, defer, defer.reference, "participant")
    _call(scenario, "DelegateTaskService", "resumeDelegateTaskRepair", Vector("--input", _json(defer)))
      .business[V.GoalTaskRepairContinuationDescriptionResult].outcome shouldBe "deferred"
    Then("description Defer and refusal execute no checks and leave the frontier exact")
    _call(scenario, "DelegateTaskService", "describeDelegateTaskRepairContinuation",
      Vector("--input", _json(V.GoalTaskRepairContinuationDescriptionInput(session))))
      .business[V.GoalTaskRepairContinuationDescriptionResult].target shouldBe description.target
    Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe commands
    When("compatible owned source movement precedes separately admitted RepairBeforeRetry")
    Files.writeString(root.resolve("repository/value.txt"), "expected\nverbose\n# owned movement\n")
    _prepare_("repair-continuation", "repair", description, answer, human, "participant")
    manifest = manifest.mapObject(_.add("repairDirectionEntered", Json.True).add("repairTarget", parser.parse(description.target).toOption.get)
      .add("newComponent", _component.toString.asJson)); _save_()
    val admitted = _call(scenario, "DelegateTaskService", "resumeDelegateTaskRepair", Vector("--input", _json(answer)))
    admitted.business[V.GoalTaskRepairContinuationDescriptionResult].outcome shouldBe "resumed-awaiting-advance"
    val repeated = _call(scenario, "DelegateTaskService", "resumeDelegateTaskRepair", Vector("--input", _json(answer)))
    repeated.business[V.GoalTaskRepairContinuationDescriptionResult].outcome shouldBe "already-resumed"
    _call(scenario, "DelegateTaskService", "resumeDelegateTaskRepair", Vector("--input", _json(answer.copy(choice = "ContinueRepair"))))
      .exitCode should not be 0
    Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe commands
    When("separate ordinary Advance issues actual Repair for that same Task")
    val repair = _advance_()
    val repairwork = _work_(repair)
    Then("the original Incomplete and accounting remain available to ordinary semantic Repair")
    repairwork.kind shouldBe "Repair"
    repairwork.state.receipts.asJson shouldBe old.receipts.asJson
    repairwork.state.automaticCount shouldBe old.automaticCount
    repairwork.state.ordinaryCount shouldBe old.ordinaryCount
    repairwork.state.generation shouldBe old.generation
    repairwork.state.input shouldBe old.input
    _advance_().issuedWorkContext shouldBe repair.issuedWorkContext
    When("a scoped source correction removes verbose output and normal Submit admits one ordinary batch")
    Files.writeString(root.resolve("repository/value.txt"), "expected\n")
    _submit_(repair, "completed", "actual-scoped-repair", "SPECIFICATION-OWNED-repairer").exitCode shouldBe 0
    var review = _advance_()
    var remaining = 10
    while (review.work.isEmpty && remaining > 0) { review = _advance_(); remaining -= 1 }
    val reviewed = _work_(review).state
    Then("the changed candidate runs every ordinary selected check and requires an independent review")
    _work_(review).kind shouldBe "Review"
    reviewed.automaticCount shouldBe old.automaticCount + 1
    reviewed.ordinaryCount shouldBe old.ordinaryCount + 1
    reviewed.generation shouldBe old.generation + 1
    reviewed.candidate.get.tree should not be old.candidate.get.tree
    reviewed.receipts.map(_.identity) shouldBe Vector("compile", "fix-check", "test-only")
    reviewed.receipts.zip(DelegateTaskTestOnlyRecoveryFixture.checks).foreach { (receipt, check) =>
      receipt.externalCapture match {
        case None => RepositorySyncNativeMergeValidation.productSuccessful(receipt, check.kind) shouldBe true
        case Some(reference) =>
          val native = WorkflowSbtCapture.decode(_read(root.resolve("capture-root").resolve(reference.path))
            .hcursor.get[Json]("process").toOption.get).toOption.get
          WorkflowSbtCapture.externalMatches(reference, receipt.sbt.get, native) shouldBe true
          receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
          native.termination shouldBe org.goldenport.cncf.processexecution.ProcessExecutionTermination.Exited(0)
      }
    }
    Files.readAllLines(root.resolve("fix-check-executions.txt")).size() shouldBe 2
    Files.readAllLines(root.resolve("selected-body-executions.txt")).size() shouldBe manifest.hcursor.get[Int]("beforeBodyCount").toOption.get + 1
    val newcommands = Files.readString(root.resolve("sbt-command-lines.txt"))
    newcommands.linesIterator.count(_ == "compile") shouldBe commands.linesIterator.count(_ == "compile") + 1
    When("a distinct specification-owned reviewer admits actual independent review and ordinary Advance completes the local commit")
    _submit_(review, "accept", "independent-review", "SPECIFICATION-OWNED-independent-reviewer").exitCode shouldBe 0
    var completed = _advance_()
    remaining = 10
    while (completed.work.isEmpty && _state(completed.state.get).commit.isEmpty && remaining > 0) {
      completed = _advance_(); remaining -= 1
    }
    if (completed.work.nonEmpty && _work_(completed).kind == "DeliveryAcceptance") {
      _submit_(completed, "accept", "delivery-acceptance", "SPECIFICATION-OWNED-delivery-reviewer").exitCode shouldBe 0
      completed = _advance_()
    }
    val finalstate = _state(completed.state.get)
    Then("the actual selected commit completes while the original capture and every historical result remain readable")
    finalstate.commit.get.identity shouldBe scenario.candidateHead
    finalstate.commit.get.parent shouldBe old.candidate.get.head
    finalstate.reviewReference should not be empty
    finalstate.automaticCount shouldBe old.automaticCount + 1
    finalstate.ordinaryCount shouldBe old.ordinaryCount + 1
    finalstate.input shouldBe old.input
    Files.readAllBytes(capturefile).toVector shouldBe capturebytes
    val historical = _call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedSnapshot.readOperation,
      Vector("--input", _json(oldreference)))
    _state(historical.business[V.WorkflowRetainedSnapshotReadResult].material).asJson shouldBe old.asJson
    Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe newcommands
    scenario.candidateStatus shouldBe ""
    manifest = manifest.mapObject(_.add("repairCompleted", Json.True).add("finalState", finalstate.asJson)
      .add("finalReadLog", historical.invocation.stdout.toString.asJson)); _save_()
  }

  private def _complete_recorded_repair(fixture: Path): Unit = {
    Given("the same physical Session after accepted Repair, successful changed-candidate checks and independent Review")
    val manifestfile = fixture.resolve("fixture.json")
    var manifest = _read(manifestfile)
    require(manifest.hcursor.get[Boolean]("recordedIncompleteRepair").contains(true) &&
      manifest.hcursor.get[Boolean]("repairDirectionEntered").contains(true) &&
      !manifest.hcursor.get[Boolean]("repairCompleted").contains(true),
      "Select the original incomplete local completion fixture without replaying any accepted work")
    val root = Path.of(manifest.hcursor.get[String]("root").toOption.get).toRealPath()
    root.toString shouldBe manifest.hcursor.get[String]("root").toOption.get
    val session = manifest.hcursor.get[String]("sessionId").toOption.get
    val owner = manifest.hcursor.get[String]("componentInstance").toOption.get
    val old = manifest.hcursor.get[S.State]("sourceState").toOption.get
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(manifest.hcursor.get[Json]("host").toOption.get))
    val plan = manifest.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get
    host.target.root shouldBe root.resolve("repository").toRealPath().toString
    manifest.hcursor.downField("repairTarget").get[String]("sessionId").toOption.get shouldBe session
    manifest.hcursor.downField("repairTarget").get[String]("componentInstance").toOption.get shouldBe owner
    val configfile = root.resolve(".textus/config.json")
    val config = _read(configfile)
    config.hcursor.get[String]("textus.datastore.path") shouldBe manifest.hcursor.get[String]("database")
    val database = Path.of(manifest.hcursor.get[String]("database").toOption.get)
    Files.isRegularFile(database) shouldBe true
    database.toRealPath().toString shouldBe manifest.hcursor.get[String]("database").toOption.get
    database.startsWith(root) shouldBe true
    config.hcursor.get[String]("textus.component.file") shouldBe manifest.hcursor.get[String]("newComponent")
    manifest.hcursor.get[String]("newComponent").toOption.get shouldBe _component.toString
    val process = parser.parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val workspace = process.hcursor.get[Vector[Json]]("workspaces").toOption.get
      .filter(_.hcursor.get[String]("identity").contains(TypedGitHostPlanFixture.workspace))
    workspace.size shouldBe 1
    workspace.head.hcursor.get[String]("root").toOption.get shouldBe host.target.root
    val completedplan = DelegateTaskTestOnlyRecoveryFixture.completionPlan(root, process)
    Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan", completedplan.noSpaces.asJson)).noSpaces)
    val files = Files.list(root.resolve("logs"))
    val nextindex = try { import scala.jdk.CollectionConverters.*; files.iterator().asScala
      .flatMap(_.getFileName.toString.takeWhile(_.isDigit).toIntOption).max + 100 } finally files.close()
    val scenario = F.reopenInstalledRepository(root, host, plan, nextindex)
    val commands = Files.readString(root.resolve("sbt-command-lines.txt"))
    val bodies = Files.readAllLines(root.resolve("selected-body-executions.txt")).size()
    val fixchecks = Files.readAllLines(root.resolve("fix-check-executions.txt")).size()
    val capture = old.receipts.last.externalCapture.get
    val capturefile = root.resolve("capture-root").resolve(capture.path)
    val capturebytes = Files.readAllBytes(capturefile).toVector
    val oldreference = take(V.WorkflowRetainedSnapshotReference.createC(take(new org.goldenport.record.io.RecordDecoder()
      .json(manifest.hcursor.get[Json]("sourceReference").toOption.get.noSpaces))))
    When("the existing public description selects the actual current frontier and its retained snapshot")
    val description = _call(scenario, "WorkflowForceClosureService", "describeWorkflowForceClosure",
      Vector("--input", _json(V.WorkflowForceClosureDescriptionInput("DelegateTask", session))))
    description.exitCode shouldBe 0
    val target = take(WorkflowForceClosure.targetC(description.business[V.WorkflowForceClosureView].target))
    target.family shouldBe "DelegateTask"
    target.sessionId shouldBe session
    target.componentInstance shouldBe owner
    val definition = take(DelegateTaskRuntimeCatalog.loadC())
    val catalog = take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.catalogC(Vector(definition)))
    val current = take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.decodeC(target.frontier.noSpaces, catalog))
    current.identity.value shouldBe session
    current.definition shouldBe definition
    current.lifecycle shouldBe org.goldenport.cncf.workflow.WorkflowInstancePersistence.Lifecycle.Active
    current.suspension shouldBe None
    val (_, snapshot) = _snapshot(scenario, owner, session, current.revision.value)
    val ready = _state(snapshot.business[V.WorkflowRetainedSnapshotReadResult].material)
    Then("only the exact reviewed CommitReady candidate qualifies for remaining ordinary local completion")
    ready.stage shouldBe "CommitReady"
    ready.commit shouldBe None
    ready.commitExecution shouldBe None
    ready.reviewReference should not be empty
    ready.input shouldBe old.input
    ready.selected shouldBe old.selected
    ready.generation shouldBe old.generation + 1
    ready.automaticCount shouldBe old.automaticCount + 1
    ready.ordinaryCount shouldBe old.ordinaryCount + 1
    ready.candidate.get.paths shouldBe Vector("value.txt")
    ready.candidate.get.head shouldBe old.candidate.get.head
    ready.candidate.get.tree should not be old.candidate.get.tree
    Files.readString(root.resolve("repository/value.txt")) shouldBe "expected\n"
    ready.receipts.map(_.identity) shouldBe Vector("compile", "fix-check", "test-only")
    ready.receipts.zip(DelegateTaskTestOnlyRecoveryFixture.checks).foreach { (receipt, check) =>
      receipt.sbt.get.kind shouldBe check.kind
      receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      receipt.externalCapture match {
        case None =>
          receipt.processObservation should not be empty
          RepositorySyncNativeMergeValidation.productSuccessful(receipt, check.kind) shouldBe true
        case Some(reference) =>
          val association = reference.association
          association.component shouldBe owner
          association.session shouldBe session
          association.root shouldBe host.target.root
          association.branch shouldBe "main"
          association.baseHead shouldBe ready.candidate.get.head
          association.candidateTree shouldBe ready.candidate.get.tree
          association.check shouldBe check.identity
          association.ordinal shouldBe check.ordinal
          val native = WorkflowSbtCapture.decode(_read(root.resolve("capture-root").resolve(reference.path))
            .hcursor.get[Json]("process").toOption.get).toOption.get
          WorkflowSbtCapture.externalMatches(reference, receipt.sbt.get, native) shouldBe true
          native.termination shouldBe org.goldenport.cncf.processexecution.ProcessExecutionTermination.Exited(0)
      }
    }
    When("separate ordinary Advance performs only the remaining native local commit")
    var finalstate = ready
    var remaining = 10
    var advancelogs = manifest.hcursor.get[Vector[String]]("completionAdvanceLogs").getOrElse(Vector.empty)
    while (finalstate.commit.isEmpty && remaining > 0) {
      val advanced = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", session))
      advancelogs = advancelogs :+ advanced.invocation.stdout.toString
      withClue("Remaining completion log: " + advanced.invocation.stdout) { advanced.exitCode shouldBe 0 }
      val view = _view(scenario, advanced)
      withClue("Unexpected work during commit-only completion: " + view.message) {
        view.work shouldBe empty
        view.issuedWorkContext shouldBe empty
      }
      finalstate = _state(view.state.get)
      finalstate.input shouldBe ready.input
      finalstate.generation shouldBe ready.generation
      finalstate.automaticCount shouldBe ready.automaticCount
      finalstate.ordinaryCount shouldBe ready.ordinaryCount
      finalstate.receipts.asJson shouldBe ready.receipts.asJson
      remaining -= 1
    }
    Then("the actual selected commit preserves prior results, captures, accounting and all earlier logs")
    val commit = finalstate.commit.get
    commit.identity shouldBe scenario.candidateHead
    commit.parent shouldBe scenario.candidateParent
    commit.parent shouldBe ready.candidate.get.head
    commit.tree shouldBe ready.candidate.get.tree
    scenario.candidateSource(commit.tree, "value.txt") shouldBe "expected\n"
    finalstate.reviewReference shouldBe ready.reviewReference
    finalstate.input shouldBe old.input
    finalstate.generation shouldBe old.generation + 1
    finalstate.automaticCount shouldBe old.automaticCount + 1
    finalstate.ordinaryCount shouldBe old.ordinaryCount + 1
    Files.readAllBytes(capturefile).toVector shouldBe capturebytes
    val historical = _call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedSnapshot.readOperation,
      Vector("--input", _json(oldreference)))
    _state(historical.business[V.WorkflowRetainedSnapshotReadResult].material).asJson shouldBe old.asJson
    Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe commands
    Files.readAllLines(root.resolve("selected-body-executions.txt")).size() shouldBe bodies
    Files.readAllLines(root.resolve("fix-check-executions.txt")).size() shouldBe fixchecks
    scenario.candidateStatus shouldBe ""
    manifest = manifest.mapObject(_.add("repairCompleted", Json.True).add("finalState", finalstate.asJson)
      .add("finalReadLog", historical.invocation.stdout.toString.asJson)
      .add("completionStartLog", description.invocation.stdout.toString.asJson).add("completionAdvanceLogs", advancelogs.asJson))
    Files.writeString(manifestfile, manifest.noSpaces)
  }

  protected def qualify_test_only(phase: String, fixture: Path): Unit = {
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

/** Explicit OLD -> NEW TestOnly qualification on one physical store.
  * @since Nov. 19, 2026
  * @version Nov. 20, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskTestOnlyRecoveryCliSpec extends NativeDelegateTaskTestOnlyQualification {
  "Installed common TestOnly recovery" should {
    "qualify only the explicitly selected before after or collect stage on the same physical Session" in {
      val phase = sys.props.get("smworkflow.spec.testOnlyRecoveryPhase").getOrElse(fail("Select before|after|collect"))
      val fixture = Path.of(sys.props.get("smworkflow.spec.testOnlyRecoveryFixture").getOrElse(fail("Select the parent-owned absolute ignored fixture directory")))
      qualify_test_only(phase, fixture)
    }
  }
}

/** OLD produces a real ended capture; NEW continues that same physical Task.
  * All Human and reviewer judgments belong only to this isolated specification.
  * @since Nov. 20, 2026
  * @version Nov. 20, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskRecordedIncompleteRepairCliSpec extends NativeDelegateTaskTestOnlyQualification {
  "Installed recorded Incomplete repair" should {
    "preserve one physical Session from OLD capture through NEW ordinary Repair and completion" in {
      Given("the parent-selected installed CAR and one retained fixture directory")
      val phase = sys.props.get("smworkflow.spec.recordedIncompleteRepairPhase").getOrElse(fail("Select prepare-old|resume-new|complete-new"))
      val fixture = Path.of(sys.props.get("smworkflow.spec.recordedIncompleteRepairFixture")
        .getOrElse(fail("Select the same absolute ignored fixture directory")))
      When("the selected stage uses the actual installed operations")
      qualify_recorded_incomplete(phase, fixture)
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
