package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Explicit installed candidate qualification. Every business invocation uses
  * the selected installed command and retains its complete two output streams.
  * Fixture inputs are not Phase acceptance or live reviewer/provider evidence.
  * @since   Nov.  9, 2026
  * @version Nov. 11, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryDirectoryListCliSpec extends AnyWordSpec with GivenWhenThen
    with Phase69ConfiguredRepositoryFixture with NativeCommonDelegateTaskQualification {
  private val selectionFile = Path.of("target/sm-workflow/qualification/phase-6.11-installed.json").toAbsolutePath.normalize
  private lazy val selected: (Path, Option[Path], Path) = {
    val explicitCommand = sys.props.get("smworkflow.spec.installedCommand").orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
    val explicitImages = sys.props.get("smworkflow.spec.installations").orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLATIONS"))
    val explicitComponent = sys.props.get("smworkflow.spec.componentFile").orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE"))
    val fromFile = if (explicitCommand.nonEmpty) None else {
      withClue("Explicit installed fixture selection is required: " + selectionFile) { Files.isRegularFile(selectionFile) shouldBe true }
      Some(decodeSelection(parse(Files.readString(selectionFile, UTF_8)).fold(error => fail(error.message), identity)))
    }
    val command = explicitCommand.map(path => checkedPath(path, command = true)).getOrElse(fromFile.get._1)
    val images = explicitImages.map(path => checkedPath(path, command = false)).orElse(fromFile.flatMap(_._2))
    val component = explicitComponent.map(checkedComponent).orElse(fromFile.flatMap(_._3))
      .getOrElse(fail("Explicit installed feature CAR selection is required: componentFile or smworkflow.spec.componentFile"))
    (command, images, component)
  }
  private def executable: Path = selected._1
  private def installations: Option[Path] = selected._2
  private def componentFile: Path = selected._3
  override protected def commonDelegateExecutable: Path = executable
  override protected def commonDelegateInstallations: Option[Path] = installations
  override protected def commonDelegateComponentFile: Option[Path] = Some(componentFile)
  override protected def commonDelegateCall(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    call(scenario, service, operation, arguments,
      detail = operation == WorkflowRetainedWork.readOperation || operation == WorkflowRetainedSnapshot.readOperation ||
        operation == "readPreparedImport")
  private def checkedPath(value: String, command: Boolean): Path = {
    val path = Path.of(value)
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    (if (command) Files.isRegularFile(path) && Files.isExecutable(path) else Files.isDirectory(path)) shouldBe true
    path
  }
  private def checkedComponent(value: String): Path = {
    val path = Path.of(value)
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isRegularFile(path) shouldBe true
    path.getFileName.toString.endsWith(".car") shouldBe true
    path
  }
  private def decodeSelection(value: Json): (Path, Option[Path], Option[Path]) = {
    val fields = value.asObject.getOrElse(fail("Installed qualification selection must be an object"))
    fields.keys.toSet.subsetOf(Set("command", "installations", "componentFile")) shouldBe true
    val command = value.hcursor.get[String]("command").fold(error => fail(error.message), identity)
    val images = fields("installations").filterNot(_.isNull).map(json =>
      checkedPath(json.asString.getOrElse(fail("installations must be an absolute directory or null")), command = false))
    val component = fields("componentFile").filterNot(_.isNull).map(json =>
      checkedComponent(json.asString.getOrElse(fail("componentFile must be an absolute CAR or null"))))
    (checkedPath(command, command = true), images, component)
  }
  private def configure(scenario: F.Scenario)(change: io.circe.JsonObject => io.circe.JsonObject): Unit = {
    val file = scenario.root.resolve(".textus/config.json")
    val original = parse(Files.readString(file, UTF_8)).fold(error => fail(error.message), identity)
    Files.writeString(file, original.mapObject(change).noSpaces, UTF_8)
  }
  /** Fixture-owned local authentication for passive historical preparation.
    * The original public target remains unchanged and is never adopted.
    */
  private def asFixturePrincipal[A](scenario: F.Scenario, principal: String)(body: => A): A = {
    principal.matches("[A-Za-z][A-Za-z0-9_-]*") shouldBe true
    val file = scenario.root.resolve(".textus/assembly.yaml")
    val original = Files.readString(file, UTF_8)
    val changed = original.replace("      id: participant\n", "      id: " + principal + "\n")
    changed should not be original
    Files.writeString(file, changed, UTF_8)
    try body finally Files.writeString(file, original, UTF_8)
  }
  private def fresh(name: String, later: Option[String] = None, sbtRepair: Option[Path] = None): F.Scenario = {
    // Resolve the actual installed selection before arranging any fixture.
    executable
    installations
    componentFile
    val scenario = F.fresh(name, laterPlanPurpose = later, sbtRepair = sbtRepair)
    scenario.enablePreparedImportRetention()
    Files.createDirectories(scenario.root.resolve("isolated-home/.textus"))
    // Use normal explicit CAR activation while retaining the installed wrapper
    // and strict runtime Component/artifact identity checks.
    configure(scenario) { fields =>
      // Standard composition resolves its target from normal gitRepositories,
      // whereas F.fresh supplies fixed legacy gitPrograms. Select the complete
      // ordinary host plan as well as the composition, retaining the existing
      // importer workspace and its grant. Product SBT is absent except for the
      // explicitly selected standalone test-failure scenario below.
      val previous = parse(fields("textus.subsystem.process-execution.plan").get.asString.get)
        .fold(error => fail(error.message), identity)
      val normal = process_plan(scenario.root, "Equal", scenario.host.goal)
      val process = Vector("workspaces" -> "identity", "workspaceGrants" -> "workspaceId")
        .foldLeft(normal) { case (plan, (key, identityKey)) =>
          val declared = plan.hcursor.get[Vector[Json]](key).toOption.get
          val identities = declared.map(_.hcursor.get[String](identityKey).toOption.get).toSet
          val retained = previous.hcursor.get[Vector[Json]](key).toOption.get
            .filterNot(row => identities(row.hcursor.get[String](identityKey).toOption.get))
          plan.mapObject(_.add(key, Json.fromValues(declared ++ retained)))
        }
      val configuredProcess = sbtRepair.fold(process) { executable =>
        val validation = TypedSbtHostPlanFixture.plan(scenario.root, executable, Vector(1))
        def rows(plan: Json, key: String): Vector[Json] = plan.hcursor.get[Vector[Json]](key).toOption.get
        val identities = Vector("sbtPrograms" -> Vector("identity"), "workspaces" -> Vector("identity"),
          "workspaceGrants" -> Vector("componentId", "workspaceId"))
        val withWorkspaces = identities.foldLeft(process) {
          case (plan, (key, identityFields)) =>
            def identity(row: Json): Vector[Json] = identityFields.map(field => row.hcursor.get[Json](field).toOption.get)
            val merged = rows(validation, key).foldLeft(rows(plan, key)) { (retained, selected) =>
              val matching = retained.filter(row => identity(row) == identity(selected))
              require(matching.forall(_ == selected), "The selected SBT " + key + " definition must remain identical")
              retained.filterNot(row => identity(row) == identity(selected)) :+ selected
            }
            plan.mapObject(_.add(key, Json.fromValues(merged)))
        }
        val selectedGrant = rows(validation, "grants").head
        val selectedComponent = selectedGrant.hcursor.get[Json]("componentId").toOption.get
        require(rows(process, "grants").count(_.hcursor.get[Json]("componentId").toOption.contains(selectedComponent)) == 1,
          "Retain one existing Component grant for the selected SBT capability")
        withWorkspaces.mapObject(_.add("grants", Json.fromValues(rows(process, "grants").map { grant =>
          if (grant.hcursor.get[Json]("componentId").toOption.contains(selectedComponent))
            grant.mapObject(_.add("capabilities", Json.fromValues(
              (rows(grant, "capabilities") ++ rows(selectedGrant, "capabilities")).distinct)))
          else grant
        })))
      }
      fields.add("textus.component.file", Json.fromString(componentFile.toString))
        .add("textus.subsystem.process-execution.plan", Json.fromString(configuredProcess.noSpaces))
        .add("textus.resource.url.file.roots", Json.fromString(scenario.root.toString))
        .add("textus.resource.read.max-bytes", Json.fromString("1048576"))
        .remove(ThreeWorkflowRuntimeParameters.definition.name)
        .remove(WorkflowArtifactBudget.maxBytes.name)
    }
    scenario
  }
  private def call(scenario: F.Scenario, service: String, operation: String, parameters: Vector[String],
    json: Boolean = true, detail: Boolean = false, requireCompact: Boolean = true): F.Result = {
    val homeArguments = Vector("--textus.test.home.path=" + scenario.root.resolve("isolated-home"),
      "--textus.test.home.inherit.runtime=false", "--textus.test.home.inherit.repositories=false",
      "--textus.test.home.inherit.local-data=false")
    val result = scenario.callInstalled(executable, service, operation, parameters ++ homeArguments, installations,
      Some(scenario.root.resolve(".textus/assembly.yaml")), jsonOutput = json)
    val out = Files.size(result.invocation.stdout)
    val err = Files.size(result.invocation.stderr)
    // Measurements are actual terminated process files, never reserialized DTOs
    // or truncated tool display. Retain failures before asserting the contract.
    val measurement = Json.obj("operation" -> Json.fromString(service + "." + operation),
      "mode" -> Json.fromString(if (json) "json" else "text"), "detail" -> Json.fromBoolean(detail),
      "exitCode" -> Json.fromInt(result.exitCode), "stdoutBytes" -> Json.fromLong(out),
      "stderrBytes" -> Json.fromLong(err), "stdout" -> Json.fromString(result.invocation.stdout.toString),
      "stderr" -> Json.fromString(result.invocation.stderr.toString), "compactOutput" -> Json.fromBoolean(out < 65536L && err < 65536L), "command" -> Json.fromString(executable.toString),
      "installations" -> installations.fold(Json.Null)(path => Json.fromString(path.toString)),
      "componentFile" -> Json.fromString(componentFile.toString))
    Files.writeString(scenario.root.resolve("qualification-measurements.jsonl"), measurement.noSpaces + "\n", UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    if (!detail && requireCompact) {
      withClue("Complete ordinary stdout: " + result.invocation.stdout) { out should be < 65536L }
      withClue("Complete ordinary stderr: " + result.invocation.stderr) { err should be < 65536L }
    }
    result
  }
  private def repository(scenario: F.Scenario, operation: String, parameters: Vector[String], json: Boolean = true) =
    call(scenario, "RepositorySyncService", operation, parameters, json)
  private val fullState = Vector("--input", "{\"syncMode\":\"FULL_STATE\"}")
  private def encoded(value: org.goldenport.record.RecordPresentable): String = take(value.toRecord().toJsonStringC)

  "Explicit installed fixture selection" should {
    "refuse unknown keys, relative commands and nonexistent commands without a source fallback" in {
      Vector(Json.obj("command" -> Json.fromString("relative")),
        Json.obj("command" -> Json.fromString("/nonexistent/p611-command")),
        Json.obj("command" -> Json.fromString("/usr/bin/true"), "sourceFallback" -> Json.True),
        Json.obj("command" -> Json.fromString("/usr/bin/true"), "installations" -> Json.fromInt(1)),
        Json.obj("command" -> Json.fromString("/usr/bin/true"), "componentFile" -> Json.fromInt(1)),
        Json.obj("command" -> Json.fromString("/usr/bin/true"), "componentFile" -> Json.fromString("relative.car")),
        Json.obj("command" -> Json.fromString("/usr/bin/true"), "componentFile" -> Json.fromString("/nonexistent/p611.car")),
        Json.obj("command" -> Json.fromString("/usr/bin/true"), "componentFile" -> Json.fromString("/usr/bin/true")))
        .foreach(value => intercept[org.scalatest.exceptions.TestFailedException](decodeSelection(value)))
      decodeSelection(Json.obj("command" -> Json.fromString("/usr/bin/true"), "installations" -> Json.Null)) shouldBe
        (Path.of("/usr/bin/true"), None, None)
    }
  }

  "Installed directory-list entry" should {
    "describe the ordinary list route and explicit single-repository option in installed help" in {
      val scenario = fresh("p611-directory-help")
      Vector("syncRepositoryDirectories", "startRepositorySync").foreach { operation =>
        val help = call(scenario, "meta", "help", Vector(
          "org.simplemodeling.textus.SmWorkflow.RepositorySyncService." + operation), json = false)
        help.exitCode shouldBe 0
        Files.readString(help.invocation.stdout, UTF_8) should include(operation)
      }
    }

    "retain missing setup and resume the exact same Session in text and JSON without child replay" in {
      val scenario = fresh("p611-directory-setup")
      Given("the installed candidate and missing normal directory-list configuration")
      configure(scenario)(_.remove(RepositorySyncDirectoryList.declaration.name))
      val setup = repository(scenario, RepositorySyncDirectoryList.syncOperation, fullState)
        .businessBounded[V.RepositoryDirectoryBatchResult]
      setup.outcome shouldBe "ConfigurationRequired"
      setup.repositories shouldBe empty
      val session = setup.sessionId
      val revision = setup.resultReference.revision
      val textSetup = repository(scenario, RepositorySyncDirectoryList.syncOperation,
        fullState ++ Vector("--sessionId", session), json = false)
      textSetup.exitCode shouldBe 0
      Files.readString(textSetup.invocation.stdout, UTF_8) should include("ConfigurationRequired")
      Given("only corrected ordinary configuration for the originally configured fixture checkout")
      configure(scenario)(_.add(RepositorySyncDirectoryList.declaration.name,
        Json.fromString(Json.arr(Json.fromString(scenario.host.target.root)).noSpaces)))
      When("the exact outstanding aggregate revision is refreshed through a fresh installed process")
      val resumed = repository(scenario, RepositorySyncDirectoryList.refreshOperation,
        Vector("--sessionId", session, "--revision", revision)).businessBounded[V.RepositoryDirectoryBatchResult]
      Then("the same aggregate converges and its old setup result remains publicly readable")
      resumed.sessionId shouldBe session
      resumed.outcome shouldBe "Completed"
      resumed.repositories.size shouldBe 1
      repository(scenario, RepositorySyncDirectoryList.readOperation,
        Vector("--sessionId", session, "--revision", revision)).businessBounded[V.RepositoryDirectoryBatchResult] shouldBe setup
      val recollected = repository(scenario, RepositorySyncDirectoryList.syncOperation,
        fullState ++ Vector("--sessionId", session)).businessBounded[V.RepositoryDirectoryBatchResult]
      recollected shouldBe resumed
      val textResumed = repository(scenario, RepositorySyncDirectoryList.syncOperation,
        fullState ++ Vector("--sessionId", session), json = false)
      textResumed.exitCode shouldBe 0
      Files.readString(textResumed.invocation.stdout, UTF_8) should include("Completed")
      Vector(true, false).foreach { json =>
        val refused = repository(scenario, RepositorySyncDirectoryList.readOperation,
          Vector("--sessionId", session, "--revision", "999999999"), json)
        refused.exitCode should not be 0
      }
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
    }

    "finish a duplicate-selected mixed clean batch without semantic work or product SBT" in {
      val scenario = fresh("p611-directory-mixed")
      val root = scenario.root
      val variants = Vector("Equal", "LocalAhead", "RemoteAhead")
      val directories = variants.indices.map(i => root.resolve("member-" + i).resolve("repository").toString).toVector
      val template = process_plan(root, "Equal", scenario.host.goal)
      val binding = template.hcursor.get[Vector[Json]]("gitRepositories").toOption.get.head
      val repositories = variants.zipWithIndex.map { (variant, i) =>
        val member = root.resolve("member-" + i)
        Files.createDirectories(member.resolve("repository"))
        _setup_commands_(member, variant).foreach { command =>
          val selected = command.map {
            case "--initial-branch=main" => if (command.contains("--bare")) "--initial-branch=release" else "--initial-branch=work-batch"
            case argument => argument.replace("refs/heads/main", "refs/heads/release")
          }
          take(_run_setup_command_(selected))
        }
        binding.mapObject(_.add("target", Json.fromString("batch-" + i))
          .add("identity", Json.fromString("batch-git-" + i)).add("capability", Json.fromString("batch-git-" + i))
          .add("localBranch", Json.fromString("work-batch")).add("remoteBranch", Json.fromString("release"))
          .add("endpoint", Json.fromString(member.resolve("upstream.git").toString)))
      }
      val plan = template.mapObject(_.add("gitRepositories", Json.fromValues(repositories))
        .add("workspaces", Json.fromValues(directories.zipWithIndex.map { (directory, i) =>
          Json.obj("identity" -> Json.fromString("batch-" + i), "root" -> Json.fromString(directory)) }))
        .add("workspaceGrants", Json.fromValues(variants.indices.map(i => Json.obj(
          "componentId" -> Json.fromString("org.simplemodeling.textus.SmWorkflow"),
          "workspaceId" -> Json.fromString("batch-" + i), "access" -> Json.fromString("manage")))))
        .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString("org.simplemodeling.textus.SmWorkflow"),
          "capabilities" -> Json.fromValues(variants.indices.map(i => Json.fromString("batch-git-" + i)))))))
      configure(scenario)(_.add("textus.subsystem.process-execution.plan", Json.fromString(plan.noSpaces))
        .add(RepositorySyncDirectoryList.declaration.name,
          Json.fromString(Json.fromValues((directories :+ directories.head).map(Json.fromString)).noSpaces)))
      When("one installed directory-list entry synchronizes three configured non-main destinations")
      val result = repository(scenario, RepositorySyncDirectoryList.syncOperation, fullState).businessBounded[V.RepositoryDirectoryBatchResult]
      Then("each actual checkout has one terminal native Session and no added product validation")
      result.outcome shouldBe "Completed"
      result.repositories.size shouldBe 3
      result.repositories.map(_.sessionId).distinct.size shouldBe 3
      result.repositories.map(_.outcome).distinct shouldBe Vector("converged")
      val arguments = fullState ++ Vector("--sessionId", result.sessionId)
      repository(scenario, RepositorySyncDirectoryList.syncOperation, arguments).businessBounded[V.RepositoryDirectoryBatchResult] shouldBe result
      repository(scenario, RepositorySyncDirectoryList.syncOperation, arguments, json = false).exitCode shouldBe 0
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
    }
  }

  "Installed bounded Workflow families" should {
    "retain a genuine post-publication decision-needed result and exact Human checkpoint without replaying effects" in {
      qualifyCommonDelegate("decision-needed")
    }

    "retain typed work references for GoalTask DelegateTask and StandaloneFix with exact fresh-process reads" in {
      Vector("GoalTask", "DelegateTask", "StandaloneFix").foreach { family =>
        // Unlike Task's initial Implement work, test-origin StandaloneFix must
        // observe an actual failed check before issuing Repair. Reuse the normal
        // ProductCheck build and its typed Test binding only for that scenario.
        val sbt = Option.when(family == "StandaloneFix")(TypedSbtHostPlanFixture.normalExecutable)
        val scenario = fresh("p611-family-" + family.toLowerCase, sbtRepair = sbt)
        // StandaloneFix retains its concrete execution-profile preflight;
        // GoalTask and DelegateTask use their existing logical Task dispatch.
        val executionParameters = if (family == "StandaloneFix") BoundedWorkExecutionAdmissionFixture.parameters
          else BoundedWorkExecutionAdmissionFixture.logicalParameters
        configure(scenario) { fields => executionParameters.foldLeft(fields) {
          case (prior, (key, value)) => prior.add(key, Json.fromString(value))
        } }
        val common = Json.obj("repositoryIdentity" -> Json.fromString(scenario.host.goal.input.repositoryIdentity),
          "requestIdentity" -> Json.fromString("specification-" + family),
          "objective" -> Json.fromString("SPECIFICATION-OWNED bounded retained-reference qualification"),
          "ownedPaths" -> Json.fromValues((if (family == "StandaloneFix") scenario.host.goal.candidatePaths
            else Vector("docs/phase.md")).map(Json.fromString)), "checks" -> Json.arr(Json.fromString("test")),
          "conditions" -> Json.arr(Json.fromString("objective")), "sourceReferences" -> Json.arr(Json.obj(
            "identity" -> Json.fromString("specification-original-request"), "revision" -> Json.fromString("1"))),
          "reviewRequired" -> Json.False, "kind" -> Json.fromString("documentation"),
          "scalaLint" -> Json.fromString("off"), "repairLimit" -> Json.fromInt(2))
        val task = common.mapObject(_.add("sourceKind", Json.fromString("direct-instruction"))
          .add("dependencies", Json.arr()))
        // Use the generated typed delegation constructor so its original public
        // field vocabulary remains authoritative rather than guessed JSON keys.
        val contract = V.DelegateTaskContract("SPECIFICATION-OWNED-requester", "SPECIFICATION-OWNED-destination",
          "SPECIFICATION-OWNED-requester", "org.example.ReferenceFixture", "destination", "documentation",
          cats.data.NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector("remote-push"),
          cats.data.NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-Human", "1")),
          None, Vector.empty, "SPECIFICATION-OWNED selected material only")
        val input = family match {
          case "StandaloneFix" => common
          case "GoalTask" => task
          case _ => task.mapObject(_.add("delegation", DevelopmentOperationalView.json(contract.toRecord()))
            .add("submissionConditions", Json.arr()))
        }
        val inputFile = scenario.root.resolve("bounded-input.json")
        Files.writeString(inputFile, input.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
        val preparedInput = Json.obj("input" -> Json.fromString(inputFile.toUri.toASCIIString),
          "selectionId" -> Json.fromString("reference-selection"), "participant" -> Json.fromString("participant"),
          "origin" -> Json.fromString("ExplicitHuman"), "idempotencyKey" -> Json.fromString("reference-start"),
          "selectionCollection" -> Json.fromString("entity:smworkflow.cli.goal_selection"))
        val prepared = call(scenario, "NativeWorkflowPreparationService", "prepare" + family + "Start",
          Vector("--input", preparedInput.noSpaces))
        scenario.selectPreparedImport(scenario.resolvePreparedImport(prepared, installations))
        val service = family + "Service"
        final case class Control(session: String, kind: String, context: Option[V.WorkflowIssuedWorkContext],
          reference: Option[V.WorkflowRetainedWorkReference], work: Option[org.goldenport.record.Record])
        def control(result: F.Result): Control = family match {
          case "GoalTask" =>
            val value = result.businessBounded[V.GoalTaskView]
            Control(value.sessionId, value.kind, value.issuedWorkContext, value.retainedWork, value.work)
          case "DelegateTask" =>
            val value = result.businessBounded[V.DelegateTaskView]
            Control(value.sessionId, value.kind, value.issuedWorkContext, value.retainedWork, value.work)
          case _ =>
            val value = result.businessBounded[V.StandaloneFixView]
            Control(value.sessionId, value.kind, value.issuedWorkContext, value.retainedWork, value.work)
        }
        Given("the actual installed " + family + " owner and its explicit specification-owned request selection")
        val started = control(call(scenario, service, "start" + family, Vector("--input", input.noSpaces,
          "--selectionId", "reference-selection", "--participant", "participant", "--idempotencyKey", "reference-start")))
        val arguments = Vector("--sessionId", started.session)
        val issued = control(call(scenario, service, "advance" + family, arguments))
        issued.kind shouldBe "Request"
        issued.work shouldBe empty
        val reference = issued.reference.getOrElse(fail(family + " typed compact reference is absent"))
        reference.owner shouldBe family + "Workflow"
        When("a distinct installed process explicitly retrieves that exact issued revision")
        val detail = call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
          Vector("--input", encoded(reference)), detail = true).businessBounded[V.WorkflowRetainedWorkReadResult]
        detail.availability shouldBe "available"
        val original = family match {
          case "GoalTask" =>
            val value = V.GoalTaskView.createC(detail.response).toOption.getOrElse(fail("Cannot decode exact GoalTaskView detail; inspect scenario captures"))
            Control(value.sessionId, value.kind, value.issuedWorkContext, value.retainedWork, value.work)
          case "DelegateTask" =>
            val value = V.DelegateTaskView.createC(detail.response).toOption.getOrElse(fail("Cannot decode exact DelegateTaskView detail; inspect scenario captures"))
            Control(value.sessionId, value.kind, value.issuedWorkContext, value.retainedWork, value.work)
          case _ =>
            val value = V.StandaloneFixView.createC(detail.response).toOption.getOrElse(fail("Cannot decode exact StandaloneFixView detail; inspect scenario captures"))
            Control(value.sessionId, value.kind, value.issuedWorkContext, value.retainedWork, value.work)
        }
        Then("the complete legacy business view remains typed and the same pending context survives recollection")
        original.context shouldBe issued.context
        original.work should not be empty
        val checkExecutions = if (family == "StandaloneFix") {
          import StandaloneFixExecution.given
          val work = io.circe.parser.parse(take(original.work.get.toJsonStringC)).left.map(_.message)
            .flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
            .flatMap(_.as[StandaloneFixExecution.Work].left.map(_.message))
            .fold(message => fail(message), identity)
          work.kind shouldBe "Repair"
          work.state.input.reviewOrigin shouldBe empty
          work.state.receipts.map(_.identity) shouldBe Vector("test")
          work.state.receipts.head.termination shouldBe "exited"
          work.state.receipts.head.exitCode shouldBe Some(1)
          work.state.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
          work.state.receipts.head.stdout should include("PRODUCT_CHECK_ENTERED")
          issued.context.get.requiredOperation shouldBe "standalone-fix-repair-capability"
          val observed = Files.readString(scenario.root.resolve("sbt-executions.txt"), UTF_8)
          observed.linesIterator.size shouldBe 1
          Some(observed)
        } else None
        control(call(scenario, service, "advance" + family, arguments)) shouldBe issued
        call(scenario, service, "advance" + family, arguments, json = false).exitCode shouldBe 0
        checkExecutions.foreach { observed =>
          Files.readString(scenario.root.resolve("sbt-executions.txt"), UTF_8) shouldBe observed
        }
      }
    }
  }

  "Installed SplitPhase retained control" should {
    "read the complete exact partition request without a second Start or fabricated native state" in {
      val scenario = fresh("p611-split-reference")
      // This separate tiny Goal obtains the actual public Component instance;
      // a metadata label or a guessed canonical key supplies no such evidence.
      val goal = call(scenario, "GoalPhaseService", "startGoalPhase", Vector("--input", scenario.input.noSpaces))
        .businessBounded[V.GoalPhaseStartResult]
      val owner = call(scenario, "GoalPhaseService", "advanceGoalPhase", Vector("--sessionId", goal.sessionId))
        .businessBounded[V.GoalPhaseAdvanceResult].retainedWork.get.componentInstance
      import SplitPhaseTypedStartFixture as T
      import SplitPhaseExplicitPlanningInputJsonV1 as J
      import J.Purpose
      val start = T.record
      val facts = T.facts
      val inputs = Vector(
        (Purpose.Source, start.source.reference.identity, take(J.encodeSourceC(start.source))),
        (Purpose.Inventory, facts.proposal.inventory.reference.identity, take(J.encodeInventoryC(facts.proposal.inventory))),
        (Purpose.Proposal, facts.proposal.proposal.reference.identity, take(J.encodeProposalC(facts.proposal.proposal))),
        (Purpose.Boundary, facts.boundary.boundary.reference.identity, take(J.encodeBoundaryC(facts.boundary.boundary))),
        (Purpose.Ownership, facts.boundary.ownership.reference.identity, take(J.encodeOwnershipC(facts.boundary.ownership))),
        (Purpose.Handoffs, facts.boundary.handoffs.reference.identity, take(J.encodeHandoffsC(facts.boundary.handoffs))),
        (Purpose.Estimates, facts.partition.estimates.reference.identity, take(J.encodeEstimatesC(facts.partition.estimates))),
        (Purpose.PartitionPolicy, facts.partition.policy.reference.identity, take(J.encodePartitionPolicyC(facts.partition.policy))),
        (Purpose.Planning, facts.planning.reference.identity, take(J.encodePlanningC(facts.planning))))
      def row(id: String, record: org.goldenport.record.Record): Json =
        DevelopmentOperationalView.json(org.goldenport.record.Record.data("id" -> id) ++ record)
      def rows(collection: String, values: Vector[Json]): Json = Json.obj("collection" ->
        Json.fromString("entity:smworkflow.cli." + collection), "records" -> Json.fromValues(values))
      val allocation = NativeStartPreparationFixture.allocationRow(start)
      val source = NativeStartPreparationFixture.sourceRow(start)
      val selection = T.selectionFor(start.input, start.reference.identity, start.participantIdentity)
      val template = SplitPhaseExplicitPartitionContext.Template(T.original.record.reference,
        SplitPhaseExplicitPartitionContext.TemplateOwner(T.original.record.owner.requirement, T.original.record.owner.presentation), facts)
      val baseline = inputs.map { (purpose, id, wire) => rows("split_" + purpose.toString.toLowerCase,
        Vector(row(id, org.goldenport.record.Record.data(J.storageField -> wire)))) } ++ Vector(
        rows("split_starts", Vector(row(start.reference.identity, allocation))),
        rows("split_contexts", Vector(row(T.original.record.reference.identity, org.goldenport.record.Record.data(
          SplitPhaseExplicitPartitionContextJsonV1.storageField -> take(SplitPhaseExplicitPartitionContextJsonV1.encodeTemplateC(template)))))),
        rows("split_selections", Vector(row(NativeWorkflowStartBusinessKey(start.input, start.participantIdentity),
          org.goldenport.record.Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection)))))))
      def file(name: String, value: org.goldenport.record.Record): String = {
        val path = scenario.root.resolve(name + ".json")
        Files.writeString(path, take(value.toJsonStringC), UTF_8, StandardOpenOption.CREATE_NEW)
        path.toUri.toASCIIString
      }
      val preparation = V.WorkflowSplitStartPreparationInput(owner,
        file("split-allocation-reference", NativeStartPreparationFixture.allocationReference(start).toRecord()),
        file("split-allocation", allocation), file("split-source", source),
        file("split-human-reference", V.WorkflowArtifactReference("SPECIFICATION-OWNED-split-selection", "1").toRecord()),
        start.participantIdentity, "normal-development-host", java.time.Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence")
      Given("passive typed allocation/source/template inputs and the actual installed public Start preparation")
      val prepared = call(scenario, "NativeWorkflowPreparationService", "prepareSplitStart", Vector("--input", encoded(preparation)))
      prepared.exitCode shouldBe 0
      val preparedDetail = call(scenario, "NativeWorkflowPreparationService", "readPreparedImport",
        Vector("--input", encoded(prepared.businessBounded[V.WorkflowPreparationResult].retainedImport.get)), detail = true)
      val consent = parse(Files.readString(preparedDetail.invocation.stdout, UTF_8)).toOption.get
        .hcursor.get[Vector[Json]]("datastore").toOption.get
      val imported = scenario.root.resolve("split-selected-inputs.json")
      Files.writeString(imported, Json.obj("datastore" -> Json.fromValues(baseline ++ consent)).noSpaces, UTF_8,
        StandardOpenOption.CREATE_NEW)
      configure(scenario)(_.add("textus.import.data.file", Json.fromString(imported.toString)))
      val input = V.SplitPhaseStartInput(start.input.sourcePhaseIdentity, start.input.invocationMode, start.input.proposalReference)
      val started = call(scenario, "SplitPhaseService", "startSplitPhase", Vector("--input", encoded(input)))
        .businessBounded[V.SplitPhaseStartResult]
      val arguments = Vector("--sessionId", started.sessionId)
      val issued = call(scenario, "SplitPhaseService", "advanceSplitPhase", arguments).businessBounded[V.SplitPhaseAdvanceResult]
      issued.kind shouldBe "Request"
      issued.partition shouldBe empty
      val reference = issued.retainedWork.get
      reference.owner shouldBe "SplitPhaseWorkflow"
      When("a fresh process reads exactly that selected native partition revision")
      val detail = call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
        Vector("--input", encoded(reference)), detail = true).businessBounded[V.WorkflowRetainedWorkReadResult]
      val original = V.SplitPhaseAdvanceResult.createC(detail.response).toOption.getOrElse(fail("Cannot decode exact Split detail; inspect scenario captures"))
      Then("the full legacy partition view survives and ordinary re-entry keeps its actual issued identity")
      original.partition should not be empty
      original.issuedWorkContext shouldBe issued.issuedWorkContext
      val repeated = call(scenario, "SplitPhaseService", "advanceSplitPhase", arguments).businessBounded[V.SplitPhaseAdvanceResult]
      repeated.retainedWork shouldBe Some(reference)
      repeated.issuedWorkContext shouldBe issued.issuedWorkContext
      call(scenario, "SplitPhaseService", "advanceSplitPhase", arguments, json = false).exitCode shouldBe 0
    }
  }

  "Actual reported public recovery material" should {
    "acquire the unchanged incident and growing legacy targets without operating their original Session" in {
      val metadata = Path.of("target/sm-workflow/qualification/phase-6.11-incident-responses.json").toAbsolutePath.normalize
      withClue("Explicit caller-selected historical public input metadata: " + metadata) {
        Files.isRegularFile(metadata) shouldBe true
      }
      val selection = parse(Files.readString(metadata, UTF_8)).fold(error => fail(error.message), identity)
      selection.asObject.get.keys.toSet shouldBe Set("responses")
      val paths = selection.hcursor.get[Vector[String]]("responses").fold(error => fail(error.message), identity).map { name =>
        val path = Path.of(name)
        path.isAbsolute shouldBe true
        path.normalize shouldBe path
        Files.isRegularFile(path) shouldBe true
        path
      }
      paths.size shouldBe 4
      paths.distinct.size shouldBe 4
      paths.map(Files.size(_)) shouldBe Vector(37533326L, 39715102L, 47011719L, 55908377L)
      val scenario = fresh("p611-actual-incident-acquisition")
      val started = call(scenario, "GoalPhaseService", "startGoalPhase", Vector("--input", scenario.input.noSpaces))
        .businessBounded[V.GoalPhaseStartResult]
      val arguments = Vector("--sessionId", started.sessionId)
      val isolated = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments).businessBounded[V.GoalPhaseAdvanceResult]
      // Read authority is limited to these exact selected public files plus the
      // isolated scenario's own artifacts. No original repository root is added.
      configure(scenario)(_.add("textus.resource.url.file.roots",
        Json.fromString((Vector(scenario.root) ++ paths).map(_.toString).mkString(","))))
      val ordinaryPreparation = Vector.newBuilder[F.Result]
      paths.zipWithIndex.foreach { (path, index) =>
        val original = Files.readString(path, UTF_8)
        val public = parse(original).fold(error => fail(error.message), identity)
        val target = RepositorySyncRepairScopeCorrection.decodePublicTarget(public.hcursor.downField("target").focus.get)
          .fold(fail(_), identity)
        target.sessionId shouldBe "deed7183d229e256ac94f86d7d0d14ec"
        target.sessionId should not be started.sessionId
        def file(name: String, value: org.goldenport.record.RecordPresentable): String = {
          val saved = scenario.root.resolve(name + "-" + index + ".json")
          Files.writeString(saved, encoded(value), UTF_8, StandardOpenOption.CREATE_NEW)
          saved.toUri.toASCIIString
        }
        val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-passive-incident-" + index, "1")
        val answer = V.RepositorySyncRepairScopeCorrectionAnswer("Defer", Vector.empty,
          "SPECIFICATION-OWNED passive compatibility qualification; no prior lifecycle fact or adoption is asserted", false, false)
        val preparation = V.WorkflowEvidencePreparationInput("RepositorySyncRepairScopeCorrection", "adoption",
          path.toUri.toASCIIString, file("defer", answer), file("reference", reference),
          file("execution", V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None)),
          "normal-development-host", java.time.Instant.now().minusSeconds(60).toString,
          "entity:smworkflow.cli.host_evidence", Some(target.context.participantIdentity))
        asFixturePrincipal(scenario, target.context.participantIdentity) {
        Given("the exact unchanged caller-retained public response of " + Files.size(path) + " bytes")
        configure(scenario)(_.add(WorkflowArtifactBudget.maxBytes.name, Json.fromString("16777216")))
        val refused = call(scenario, "NativeWorkflowPreparationService", "prepareEvidence",
          Vector("--input", encoded(preparation)))
        refused.exitCode should not be 0
        (Files.readString(refused.invocation.stdout, UTF_8) + Files.readString(refused.invocation.stderr, UTF_8)) should
          include("runtime byte limit")
        When("only the artifact override is removed and the installed passive owner acquires the original complete input")
        configure(scenario)(_.remove(WorkflowArtifactBudget.maxBytes.name))
        val prepared = call(scenario, "NativeWorkflowPreparationService", "prepareEvidence",
          Vector("--input", encoded(preparation)))
        ordinaryPreparation += prepared
        prepared.exitCode shouldBe 0
        val compact = prepared.businessBounded[V.WorkflowPreparationResult]
        compact.status shouldBe Some("prepared")
        compact.datastore shouldBe empty
        val referenceToImport = compact.retainedImport.get
        val repeated = call(scenario, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", encoded(preparation)))
          .businessBounded[V.WorkflowPreparationResult]
        repeated.retainedImport shouldBe compact.retainedImport
        val detail = call(scenario, "NativeWorkflowPreparationService", "readPreparedImport",
          Vector("--input", encoded(referenceToImport)), detail = true)
        detail.exitCode shouldBe 0
        val legacy = call(scenario, "NativeWorkflowPreparationService", "prepareEvidence",
          Vector("--input", encoded(preparation), "--preparationFormat", "legacy-full"), detail = true)
        legacy.exitCode shouldBe 0
        val exported = parse(Files.readString(detail.invocation.stdout, UTF_8)).fold(error => fail(error.message), identity)
        exported shouldBe parse(Files.readString(legacy.invocation.stdout, UTF_8)).fold(error => fail(error.message), identity)
        val comparison = Json.obj("sourceBytes" -> Json.fromLong(Files.size(path)),
          "ordinaryStdoutBytes" -> Json.fromLong(Files.size(prepared.invocation.stdout)),
          "ordinaryStderrBytes" -> Json.fromLong(Files.size(prepared.invocation.stderr)),
          "fullBaselineStdoutBytes" -> Json.fromLong(Files.size(legacy.invocation.stdout)),
          "detailStdoutBytes" -> Json.fromLong(Files.size(detail.invocation.stdout)))
        Files.writeString(scenario.root.resolve("preparation-transfer-comparison.jsonl"), comparison.noSpaces + "\n", UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        Files.size(prepared.invocation.stdout) should be < Files.size(legacy.invocation.stdout)
        call(scenario, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", encoded(preparation)), json = false).exitCode shouldBe 0
        val rows = exported.hcursor.get[Vector[Json]]("datastore").toOption.get
          .flatMap(_.hcursor.get[Vector[Json]]("records").toOption.get)
        rows.size shouldBe 1
        val evidence = take(NativeExternalEvidence.decodeStorageC(rows.head.hcursor
          .get[String](NativeExternalEvidence.storageField).toOption.get))
        val scope = evidence.binding.hcursor.downField("repositoryRepairScopeCorrection")
        val preserved = RepositorySyncRepairScopeCorrection.decodeTarget(scope.downField("target").focus.get).fold(fail(_), identity)
        Then("explicit retained detail and legacy-full output preserve the complete original target and its UNKNOWN/failure provenance")
        (preserved == target) shouldBe true
        scope.downField("answer").get[String]("choice").toOption shouldBe Some("Defer")
        scope.downField("answer").get[Boolean]("priorExecutionEnded").toOption shouldBe Some(false)
        scope.downField("answer").get[Boolean]("acceptOriginalUncertainty").toOption shouldBe Some(false)
        (Files.readString(path, UTF_8) == original) shouldBe true
        // Never selectPreparedImport on this export: no old-Session evidence is
        // provisioned, no old target is rebound and no original effect executes.
        }
        val recollected = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments).businessBounded[V.GoalPhaseAdvanceResult]
        recollected.retainedWork shouldBe isolated.retainedWork
        recollected.issuedWorkContext shouldBe isolated.issuedWorkContext
        recollected.repairProgress shouldBe isolated.repairProgress
      }
      // Ordinary controls and explicit full-detail captures have separate actual
      // stream measurements; no historical Session is operated or rebound.
      ordinaryPreparation.result().foreach { result =>
        withClue("Ordinary prepareEvidence must return confirmed concise owner references; complete measurement: " +
          result.invocation.stdout) { Files.size(result.invocation.stdout) should be < 65536L }
      }
    }
  }

  "Installed derived preparation storage" should {
    "report missing setup, preserve explicit legacy output and bind confirmed detail to its exact owner" in {
      val scenario = fresh("p611-prepared-import-owner")
      val config = parse(Files.readString(scenario.root.resolve(".textus/config.json"), UTF_8)).toOption.get
      val policy = config.hcursor.downField(CommonEvidence.declaration.name).focus.get
      configure(scenario)(_.remove(CommonEvidence.declaration.name))
      val args = Vector("--input", encoded(scenario.selectedGoalPreparationInput))
      Given("valid passive source files with no configured CommonEvidence retention policy")
      val missing = call(scenario, "NativeWorkflowPreparationService", "prepareGoalStart", args)
        .businessBounded[V.WorkflowPreparationResult]
      missing.status shouldBe Some("setup-required")
      missing.retainedImport shouldBe empty
      missing.datastore shouldBe empty
      val legacy = call(scenario, "NativeWorkflowPreparationService", "prepareGoalStart", args ++ Vector("--preparationFormat", "legacy-full"), detail = true)
      legacy.exitCode shouldBe 0
      val baseline = parse(Files.readString(legacy.invocation.stdout, UTF_8)).toOption.get
      baseline.hcursor.get[Vector[Json]]("datastore").toOption.get should not be empty
      configure(scenario)(_.add(CommonEvidence.declaration.name, policy))
      When("the normal policy is restored and the ordinary public preparation stores its derived importer")
      val saved = call(scenario, "NativeWorkflowPreparationService", "prepareGoalStart", args)
        .businessBounded[V.WorkflowPreparationResult]
      saved.status shouldBe Some("prepared")
      val reference = saved.retainedImport.get
      val detail = call(scenario, "NativeWorkflowPreparationService", "readPreparedImport", Vector("--input", encoded(reference)), detail = true)
      detail.exitCode shouldBe 0
      parse(Files.readString(detail.invocation.stdout, UTF_8)).toOption.get shouldBe baseline
      Then("foreign participant, Component, purpose and selected revision cannot retrieve the importer")
      Vector(reference.copy(participantIdentity = "foreign-participant"), reference.copy(componentInstance = "foreign-component"),
        reference.copy(operation = "prepareEvidence"), reference.copy(evidence = reference.evidence.copy(revision = "2"))).foreach { other =>
        call(scenario, "NativeWorkflowPreparationService", "readPreparedImport", Vector("--input", encoded(other))).exitCode should not be 0
      }
      val forgedParticipant = scenario.selectedGoalPreparationInput.copy(participant = "foreign-participant")
      call(scenario, "NativeWorkflowPreparationService", "prepareGoalStart", Vector("--input", encoded(forgedParticipant))).exitCode should not be 0
      // Consume only actual selected detail through the existing native importer;
      // the prepared status itself is never a check or acceptance observation.
      scenario.selectPreparedImport(detail)
      call(scenario, "GoalPhaseService", "startGoalPhase", Vector("--input", scenario.input.noSpaces))
        .businessBounded[V.GoalPhaseStartResult].sessionId should not be empty
      call(scenario, "NativeWorkflowPreparationService", "prepareGoalStart", args, json = false).exitCode shouldBe 0
    }
  }

  "Installed retained control and selected detail" should {
    "correct a large nested result acquisition limit in the same Session and retain the one accepted result" in {
      Vector(37500000, 45000000).foreach { bytes =>
        val scenario = fresh("p611-result-budget-" + bytes)
        val started = call(scenario, "GoalPhaseService", "startGoalPhase", Vector("--input", scenario.input.noSpaces))
          .businessBounded[V.GoalPhaseStartResult]
        val arguments = Vector("--sessionId", started.sessionId)
        val issued = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments).businessBounded[V.GoalPhaseAdvanceResult]
        val nested = Json.obj("historicalTargets" -> Json.arr(Json.obj("snapshot" -> Json.obj(
          "logs" -> Json.obj("stdout" -> Json.fromString("P611_NESTED_LOG_DETAIL_ONLY_" + ("x" * bytes)),
            "stderr" -> Json.fromString("retained prior diagnostic")))))).noSpaces
        val result = Json.obj("outcome" -> Json.fromString("decision-needed"),
          "summary" -> Json.fromString("SPECIFICATION-OWNED explicit bounded decision"),
          "findings" -> Json.arr(Json.fromString(nested)))
        val file = scenario.root.resolve("large-result.json")
        Files.writeString(file, result.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
        Files.size(file) should be > bytes.toLong
        Files.size(file) should be < 67108864L
        val submit = Vector("--sessionId", started.sessionId, "--issuedWorkContext", encoded(issued.issuedWorkContext.get),
          "--resultResource", file.toUri.toASCIIString, "--resultReference",
          encoded(V.WorkflowArtifactReference("specification-large-result-" + bytes, "1")))
        Given("one actually issued assignment and complete nested result above the isolated normal home 16 MiB artifact ceiling")
        val homeConfig = scenario.root.resolve("isolated-home/.textus/config.json")
        Files.writeString(homeConfig, Json.obj(WorkflowArtifactBudget.maxBytes.name -> Json.fromString("16777216")).noSpaces, UTF_8)
        Vector(true, false).foreach { json =>
          val refused = call(scenario, "GoalPhaseService", "submitGoalPhaseWorkResult", submit, json)
          refused.exitCode should not be 0
          val diagnostics = Files.readString(refused.invocation.stdout, UTF_8) + Files.readString(refused.invocation.stderr, UTF_8)
          diagnostics should include("runtime byte limit")
        }
        val stillIssued = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments).businessBounded[V.GoalPhaseAdvanceResult]
        stillIssued.issuedWorkContext shouldBe issued.issuedWorkContext
        stillIssued.retainedWork shouldBe issued.retainedWork
        stillIssued.repairProgress shouldBe issued.repairProgress
        Given("an assembly Component instance 16 MiB limit with no higher-priority runtime override")
        // CNCF initialization places RuntimeConfiguration above the assembly
        // SubsystemInstance layer. Clear the home value to select the Component
        // limit itself; project 64 MiB would correctly shadow that limit.
        Files.writeString(homeConfig, "{}", UTF_8)
        val descriptor = scenario.root.resolve(".textus/assembly.yaml")
        val originalDescriptor = Files.readString(descriptor, UTF_8)
        val componentLimit = originalDescriptor.replace("    version: 0.1.0-SNAPSHOT\n",
          "    version: 0.1.0-SNAPSHOT\n    config:\n      workflow.artifact.max-bytes: '16777216'\n")
        componentLimit should not be originalDescriptor
        Files.writeString(descriptor, componentLimit, UTF_8)
        val componentRefused = call(scenario, "GoalPhaseService", "submitGoalPhaseWorkResult", submit)
        componentRefused.exitCode should not be 0
        (Files.readString(componentRefused.invocation.stdout, UTF_8) +
          Files.readString(componentRefused.invocation.stderr, UTF_8)) should include("runtime byte limit")
        Files.writeString(descriptor, originalDescriptor, UTF_8)
        Files.writeString(homeConfig, Json.obj(WorkflowArtifactBudget.maxBytes.name -> Json.fromString("16777216")).noSpaces, UTF_8)
        configure(scenario)(_.add(WorkflowArtifactBudget.maxBytes.name, Json.fromString("67108864")))
        When("normal configuration corrects the artifact limit in the same Session while generic Resource stays at one MiB")
        if (bytes == 37500000) {
          configure(scenario)(_.remove(WorkflowArtifactBudget.maxBytes.name))
          Files.writeString(homeConfig, "{}", UTF_8)
        }
        // 37.5 MB selects the absent-property 64 MiB default; the growing case
        // keeps project 64 MiB over home 16 MiB after removing the Component override.
        val accepted = call(scenario, "GoalPhaseService", "submitGoalPhaseWorkResult", submit)
          .businessBounded[V.GoalPhaseWorkSubmissionResult]
        accepted.accepted shouldBe true
        accepted.sessionId shouldBe started.sessionId
        val question = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments).businessBounded[V.GoalPhaseAdvanceResult]
        question.kind shouldBe "Human"
        question.human shouldBe empty
        val reference = question.retainedWork.get
        val detail = call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
          Vector("--input", encoded(reference)), detail = true).businessBounded[V.WorkflowRetainedWorkReadResult]
        val original = V.GoalPhaseAdvanceResult.createC(detail.response).toOption.getOrElse(fail("Cannot decode exact Goal detail; inspect scenario captures"))
        Then("public selected detail contains the entire one accepted result while ordinary recollection stays compact")
        (original.human.get.requestingResult.findings == Vector(nested)) shouldBe true
        original.human.get.requestingResult.outcome shouldBe "decision-needed"
        val repeated = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments).businessBounded[V.GoalPhaseAdvanceResult]
        repeated.retainedWork shouldBe Some(reference)
        repeated.issuedWorkContext shouldBe question.issuedWorkContext
        repeated.repairProgress shouldBe question.repairProgress
        call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments, json = false).exitCode shouldBe 0
        // There is exactly one successful Submit, no accepted Submit replay and
        // no re-Start. These fixture results do not invent an UNKNOWN execution.
        Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "measure 37.5 MB and growing full material against ordinary text and JSON after fresh process reads" in {
      Vector(37500000, 45000000).foreach { bytes =>
        val sentinel = "P611_SELECTED_DETAIL_ONLY_"
        val body = sentinel + ("x" * (bytes - sentinel.length))
        val scenario = fresh("p611-retained-" + bytes, Some(body))
        // This large Plan also needs the existing, independent mechanical
        // receipt capacity. It is not supplied by the artifact read budget.
        configure(scenario)(_.add("workflow.mechanical-receipt.max-bytes", Json.fromString("67108864")))
        Given("an ordinary selected first Step with unrelated retained later Plan material of " + bytes + " UTF-8 bytes")
        val started = call(scenario, "GoalPhaseService", "startGoalPhase", Vector("--input", scenario.input.noSpaces))
          .businessBounded[V.GoalPhaseStartResult]
        val arguments = Vector("--sessionId", started.sessionId)
        val control = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments).businessBounded[V.GoalPhaseAdvanceResult]
        control.kind shouldBe "Request"
        control.work shouldBe empty
        val reference = control.retainedWork.getOrElse(fail("Confirmed retained owner reference is absent"))
        val context = control.issuedWorkContext
        When("fresh installed processes render text control, read exact selected detail and recollect pending work")
        val text = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments, json = false)
        text.exitCode shouldBe 0
        Files.readString(text.invocation.stdout, UTF_8) should not include sentinel
        Files.readString(text.invocation.stderr, UTF_8) should not include sentinel
        val selected = call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
          Vector("--input", encoded(reference)), detail = true)
        val detail = selected.businessBounded[V.WorkflowRetainedWorkReadResult]
        detail.availability shouldBe "available"
        val original = V.GoalPhaseAdvanceResult.createC(detail.response).toOption.getOrElse(fail("Cannot decode exact Goal detail; inspect scenario captures"))
        Then("full detail is complete, exact issued identity is unchanged and ordinary output omits the body")
        (original.work.get.selectedPlan.steps.last.purpose == body) shouldBe true
        original.issuedWorkContext shouldBe context
        Files.size(selected.invocation.stdout) should be > bytes.toLong
        val repeated = call(scenario, "GoalPhaseService", "advanceGoalPhase", arguments).businessBounded[V.GoalPhaseAdvanceResult]
        repeated.retainedWork shouldBe Some(reference)
        repeated.issuedWorkContext shouldBe context
        val crossed = WorkflowRetainedWork.publicReference(take(WorkflowRetainedWork.fromPublicC(reference))
          .copy(componentInstance = "foreign-component-instance"))
        Vector(true, false).foreach { json =>
          call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
            Vector("--input", encoded(crossed)), json).exitCode should not be 0
        }
      }
    }
  }
}
