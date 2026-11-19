package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskStartInput
import DelegateTaskValidationRecovery.given

/** Managed validation recovery and retained-capture qualification.
  * @since   Nov. 12, 2026
  * @version Nov. 19, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
class DelegateTaskValidationRecoveryOperationSpec extends AnyWordSpec with Matchers with org.scalatest.GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = super.process_plan(root, if (Files.exists(root.resolve("SPEC-test-only-recovery"))) "Goal" else scenario, goal)
    if (Files.exists(root.resolve("SPEC-test-only-recovery"))) return DelegateTaskTestOnlyRecoveryFixture.processPlan(root, git)
    val baseline0 = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2), Some(git),
      operation = SbtOperation.Test, stdoutBytes = 2097152, stderrBytes = 131072)
    def array(value: Json, name: String) = value.hcursor.get[Vector[Json]](name).toOption.get
    val baseline = if (!Files.exists(root.resolve("SPEC-review-fixture"))) baseline0 else {
      val focused = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(3),
        operation = SbtOperation.TestOnly, testClasses = Vector("org.simplemodeling.textus.smworkflow.admission.metafixture.SelectedMetadataFixtureSpec"))
      val compile = if (Files.exists(root.resolve("SPEC-delivery-fixture")))
        array(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), operation = SbtOperation.Compile), "sbtPrograms")
        else Vector.empty
      val programs = array(baseline0, "sbtPrograms") ++ array(focused, "sbtPrograms") ++ compile
      val capabilities = (array(baseline0, "gitPrograms") ++ programs).map(_.hcursor.get[Json]("capability").toOption.get)
      baseline0.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
        .add("workspaces", Json.fromValues(array(baseline0, "workspaces") ++ array(focused, "workspaces")))
        .add("workspaceGrants", Json.fromValues(array(baseline0, "workspaceGrants") ++ array(focused, "workspaceGrants")))
        .add("grants", Json.arr(Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> Json.fromValues(capabilities)))))
    }
    val plan = if (!Files.exists(root.resolve("SPEC-new-capture-policy"))) baseline else {
      val programs = baseline.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map(_.mapObject(program =>
        program.add("limits", program("limits").get.mapObject(_.add("stdoutBytes", Json.fromLong(8L * 1024 * 1024))))))
      baseline.mapObject(_.add("sbtPrograms", programs.asJson))
    }
    val workspace = Json.obj("identity" -> "sm-workflow-validation-artifacts".asJson,
      "root" -> root.resolve("capture-root").toString.asJson)
    val grant = Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "workspaceId" -> "sm-workflow-validation-artifacts".asJson, "access" -> "manage".asJson)
    val readWorkspace = Json.obj("identity" -> "spec-returned-capture-read".asJson,
      "root" -> root.resolve("capture-root").toString.asJson)
    val readGrant = Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "workspaceId" -> "spec-returned-capture-read".asJson, "access" -> "read-only".asJson)
    plan.mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(_ ++ Vector(workspace, readWorkspace)))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ ++ Vector(grant, readGrant))))
  }
  "The managed external capture owner" should {
    "archive and discover exact TestOnly outcomes while refusing changed bytes and foreign owners" in {
      Given("typed TestOnly result specimens and a finite artifact owner; these specimens claim no process execution")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/recovery-test-only-capture").toAbsolutePath), "SPEC-").toRealPath()
      Files.createDirectories(root.resolve("capture-root/validation"))
      val association = WorkflowSbtCapture.Association("SPEC-component", "SPEC-session", 7L, "participant", "test-only", 3,
        root.resolve("repository").toString, "main", "a" * 40, "b" * 40)
      val limits = org.goldenport.cncf.resource.ResourceReadLimits(1048576)
      val empty = ProcessExecutionCapture(Vector.empty, 0L, false)
      Vector(WorkflowSbtValidation.Outcome.Succeeded, WorkflowSbtValidation.Outcome.Failed,
        WorkflowSbtValidation.Outcome.Incomplete).zipWithIndex.foreach { (outcome, index) =>
        val incomplete = outcome == WorkflowSbtValidation.Outcome.Incomplete
        val stdout = ProcessExecutionCapture(Vector.fill(32)(0x61.toByte), 32L, incomplete)
        val process = ProcessExecutionResult(if (incomplete) ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)
          else ProcessExecutionTermination.Exited(if (outcome == WorkflowSbtValidation.Outcome.Succeeded) 0 else 1), stdout, empty,
          Vector.empty, 1L, "selected-sbt")
        val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.TestOnly, outcome,
          Option.when(outcome == WorkflowSbtValidation.Outcome.Succeeded)(WorkflowSbtValidation.Selection(Vector("example.SelectedSpec"), 1, 1L)), None)
        val path = "validation/SPEC-" + index + ".json"
        When("the owner archives the full canonical artifact and a fresh call discovers the same bytes")
        _with_runtime_(root, "Equal") { (_, _, _, prepare) => prepare { call => for {
          reference <- WorkflowSbtCapture.archiveC(call.core, "sm-workflow-validation-artifacts", path, association, facts, process, limits)
          discovered <- WorkflowSbtCapture.discoverC(call.core, "sm-workflow-validation-artifacts", path, association, limits)
          reopened <- WorkflowSbtCapture.hydrateC(call.core, reference, association, facts, limits)
          _ = Then("the typed specimen's outcome and bytes remain exact and a foreign owner is refused")
          _ <- if (discovered == ((reference, facts, process)) && reopened == process &&
            WorkflowSbtCapture.discoverC(call.core, "sm-workflow-validation-artifacts", path,
              association.copy(session = "foreign"), limits).isFaillure) org.goldenport.Consequence.unit
            else org.goldenport.Consequence.stateConflict("SPEC TestOnly owner or actual outcome changed")
        } yield () } }
        When("the stored artifact is changed before new discovery requests inspect it and an absent path")
        Files.writeString(root.resolve("capture-root").resolve(path), "unreadable canonical artifact")
        _with_runtime_(root, "Equal") { (_, _, _, prepare) => prepare { call =>
          val changed = WorkflowSbtCapture.discoverC(call.core, "sm-workflow-validation-artifacts", path, association, limits)
          val absent = WorkflowSbtCapture.discoverC(call.core, "sm-workflow-validation-artifacts", "validation/absent.json", association, limits)
          Then("neither the malformed artifact nor the absent artifact can be collected")
          if (changed.isFaillure && absent.isFaillure) org.goldenport.Consequence.unit
          else org.goldenport.Consequence.stateConflict("SPEC changed or absent capture was collected")
        } }
      }
    }
    "create actual complete output and reopen the same File from a fresh host" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/recovery-capture").toAbsolutePath),
        "SPEC-").toRealPath()
      Files.createDirectories(root.resolve("capture-root/validation"))
      val a = WorkflowSbtCapture.Association("SPEC-component", "SPEC-session", 17L, "participant", "test", 3,
        root.resolve("repository").toString, "main", "a" * 40, "b" * 40)
      val output = Vector.fill(256 * 1024)(0x61.toByte)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0),
        ProcessExecutionCapture(output, output.size.toLong, false), ProcessExecutionCapture(Vector.empty, 0L, false),
        Vector.empty, 100L, "selected-sbt")
      val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Succeeded, None, None)
      val limits = org.goldenport.cncf.resource.ResourceReadLimits(1024 * 1024)
      var retained = Option.empty[WorkflowSbtCapture.ExternalReference]
      _with_runtime_(root, "Equal") { (_, _, _, prepare) =>
        prepare { call => WorkflowSbtCapture.archiveC(call.core, "sm-workflow-validation-artifacts", "validation/SPEC.json",
          a, facts, process, limits).map { ref => retained = Some(ref); () } }
      }
      Files.size(root.resolve("capture-root/validation/SPEC.json")) should be > output.size.toLong
      _with_runtime_(root, "Equal") { (_, _, _, prepare) =>
        prepare { call => for {
          reopened <- WorkflowSbtCapture.hydrateC(call.core, retained.get, a, facts, limits)
          _ <- if (reopened == process) org.goldenport.Consequence.unit
            else org.goldenport.Consequence.stateConflict("SPEC reopened bytes differ")
          discovered <- WorkflowSbtCapture.discoverC(call.core, "sm-workflow-validation-artifacts", "validation/SPEC.json", a, limits)
          _ <- if (discovered == ((retained.get, facts, process))) org.goldenport.Consequence.unit
            else org.goldenport.Consequence.stateConflict("SPEC discovery differs")
        } yield () }
      }
    }
  }
  "The selected TestOnly stopped boundary" should {
    "retain actual Compile and FixCheck across recorded Incomplete direction and one remaining attempt" in {
      import org.goldenport.Consequence
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
      Given("a tiny real metadata project with an ordinary native Compile/FixCheck/TestOnly batch")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/recorded-test-only").toAbsolutePath), "SPEC-").toRealPath()
      DelegateTaskTestOnlyRecoveryFixture.build(root, Some(Path.of("target/sm-workflow/validation-classpath.txt").toAbsolutePath.normalize))
      Files.writeString(root.resolve("SPEC-test-only-recovery"), "SPECIFICATION-OWNED ordinary selected pipeline\n")
      val checks = DelegateTaskTestOnlyRecoveryFixture.checks
      val initial = ThreeWorkflowNormalHostFixture.definition(root)
      val host = initial.copy(goal = initial.goal.copy(candidatePaths = Vector("value.txt"),
        stepValidation = initial.goal.stepValidation.copy(checks = checks)))
      val parameters = BoundedWorkExecutionAdmissionFixture.logicalParameters ++ Map("workflow.mechanical-receipt.max-bytes" -> "67108864")
      val input = V.DelegateTaskInput("sm-workflow", "SPEC-recorded-TestOnly", "SPECIFICATION-OWNED bounded selected recovery",
        NonEmptyVector.one("value.txt"), NonEmptyVector.of("compile", "fix-check", "test-only"), NonEmptyVector.one("behavior"),
        NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-source", "1")), true, "code", "off", 10,
        "direct-instruction", Vector.empty, V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component",
          "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
          Vector.empty, NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-authority", "1")), None, Vector.empty, "SPEC-only"), Vector("behavior"))
      _with_runtime_(root, "Equal", Some(host), parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def _call_(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
        val selected = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "SPEC-input.json", "selection-id" -> "SPEC-recorded-selection",
          "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-recorded-key",
          "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
        prepare { setup =>
          val row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val started = read_value[V.DelegateTaskView](take(_call_("startDelegateTask", List(Property("input", input, None),
          Property("selectionId", "SPEC-recorded-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-recorded-key", None)))))
        val session = started.sessionId
        val implement = read_value[V.DelegateTaskView](take(_call_("advanceDelegateTask", List(Property("sessionId", session, None)))))
        Files.writeString(root.resolve("repository/value.txt"), "expected\n")
        val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-implement", "1")
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED-worker"), None,
          Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPECIFICATION-OWNED-worker", "ordinary")))
        val result = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED fixture input", Vector.empty,
          NonEmptyVector.one(V.DelegateTaskCondition("behavior", true, "SPEC selected fixture")), Vector.empty, None, None)
        take(_call_("submitDelegateTaskWorkResult", List(Property("sessionId", session, None), Property("issuedWorkContext", implement.issuedWorkContext.get, None),
          Property("result", result, None), Property("resultReference", reference, None), Property("executionEvidence", execution, None))))
        When("ordinary Advance executes the actual selected batch and receives a real output-limit termination")
        val stopped = read_value[V.DelegateTaskView](take(_call_("advanceDelegateTask", List(Property("sessionId", session, None)))))
        Then("the actual stopped batch is exposed as Wait")
        stopped.kind shouldBe "Wait"
        val before = Files.readString(root.resolve("sbt-command-lines.txt"))
        val bodies = Files.readString(root.resolve("selected-body-executions.txt"))
        When("the stopped Session's recovery description is requested")
        val description = read_value[V.DelegateTaskValidationRecoveryView](take(_call_("describeDelegateTaskValidationRecovery",
          List(Property("input", V.DelegateTaskValidationRecoveryDescriptionInput(session), None)))))
        val target = take(DelegateTaskValidationRecovery.targetC(description.target))
        Then("the recovery source names the genuinely recorded Incomplete and its successful actual prefix")
        target.check.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
        target.retainedOutcome shouldBe Some("incomplete")
        target.successfulPrefix shouldBe Vector("compile", "fix-check")
        target.captureAvailability shouldBe "available"
        Files.readString(root.resolve("fix-check-executions.txt")).linesIterator.size shouldBe 1
        When("ordinary Advance is repeated at the stopped frontier without a recovery direction")
        take(_call_("advanceDelegateTask", List(Property("sessionId", session, None))))
        Then("the repeated Advance executes no check")
        Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe before
        Given("an exact specification-owned terminal proof and Human direction without installed evidence")
        val proof = DelegateTaskValidationRecovery.Proof("SPECIFICATION-OWNED-real-output-limit", "SPECIFICATION-OWNED-returned-caller",
          "output-limit-exceeded", None, "completed", 0, "host-attested", "available", "unknown")
        val human = ContextReference("SPECIFICATION-OWNED-exact-Human-direction", "1")
        val binding = take(DelegateTaskValidationRecovery.bindingC(target, proof, "participant", human))
        val recovery = V.DelegateTaskValidationRecoveryInput(session, description.target, proof.asJson.noSpaces, "participant",
          V.WorkflowArtifactReference(human.identity, human.revision))
        When("recovery is requested before that evidence is admitted")
        val unadmitted = _call_("recoverDelegateTaskValidation", List(Property("input", recovery, None)))
        Then("the unadmitted direction is refused")
        unadmitted.isSuccess shouldBe false
        Given("the independently installed specification-owned evidence for that exact direction")
        prepare(_.evidenceC(NativeExternalEvidence.Purpose.DelegateTaskValidationRecovery,
          ThreeWorkflowExternalEvidencePorts.key(human.identity, human.revision), human, "participant", binding))
        When("the specification-owned exact direction is admitted without executing a check")
        val admitted = read_value[V.DelegateTaskValidationRecoveryView](take(_call_("recoverDelegateTaskValidation",
          List(Property("input", recovery, None)))))
        Then("the direction awaits separate Advance and no check has executed")
        admitted.outcome shouldBe "authorized-awaiting-advance"
        Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe before
        When("the same direction is submitted again")
        val repeated = _call_("recoverDelegateTaskValidation", List(Property("input", recovery, None)))
        Then("the repeated direction is refused")
        repeated.isSuccess shouldBe false
        Given("the same product input with the specification-owned output switch disabled")
        Files.delete(root.resolve("flood-enabled"))
        When("a separate ordinary Advance consumes the admitted same-check attempt")
        take(_call_("advanceDelegateTask", List(Property("sessionId", session, None))))
        Then("only one remaining selected body executes and native history retains the original Incomplete")
        Files.readString(root.resolve("selected-body-executions.txt")).linesIterator.size shouldBe bodies.linesIterator.size + 1
        Files.readString(root.resolve("fix-check-executions.txt")).linesIterator.size shouldBe 1
        val commands = Files.readString(root.resolve("sbt-command-lines.txt"))
        commands.linesIterator.count(_ == "compile") shouldBe before.linesIterator.count(_ == "compile")
        When("ordinary Advance is repeated after the selected result was retained")
        take(_call_("advanceDelegateTask", List(Property("sessionId", session, None))))
        Then("the repeated Advance executes no additional check")
        Files.readString(root.resolve("sbt-command-lines.txt")) shouldBe commands
        When("the exact original and successor native checkpoints are read")
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_.copy(mechanicalReceiptMaxBytes = Some(67108864)), _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("value.txt"), checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC retained Session absent")))
          old <- DelegateTaskNativeRun.loadPayloadC(store, session, target.frontierRevision, setup.core)
          next <- DelegateTaskNativeRun.loadPayloadC(store, session, target.frontierRevision + 3, setup.core)
          _ = Then("native history preserves the original Incomplete, successful prefix, candidate and accounting")
          _ <- if (old.state.stage == "Wait" && old.state.receipts.last.sbt.exists(_.outcome == WorkflowSbtValidation.Outcome.Incomplete) &&
            old.state.receipts.last.processObservation.exists(_.stdout.truncated) &&
            next.state.receipts.take(2) == old.state.receipts.take(2) && next.state.receipts.last.externalCapture.nonEmpty &&
            RepositorySyncNativeMergeValidation.productSuccessful(next.state.receipts.last, WorkflowSbtValidation.Kind.TestOnly) &&
            next.state.automaticCount == old.state.automaticCount && next.state.ordinaryCount == old.state.ordinaryCount &&
            next.state.candidate == old.state.candidate && next.state.conditionReport == old.state.conditionReport)
            Consequence.unit else Consequence.stateConflict("SPEC recovery replayed prefix or changed native outcome/accounting")
        } yield () }
      }
    }
  }
  private[admission] def qualifyInstalledAvailableRecovery(executable: Path, component: Path): Unit =
    qualifyAvailableRecovery(Some(executable -> component))

  private[admission] def qualifyReturnedCapture(installed: Option[(Path, Path)]): Unit =
    qualifyAvailableRecovery(installed, delivery = true)

  private def qualifyAvailableRecovery(installed: Option[(Path, Path)], delivery: Boolean = false): Unit = {
      if (delivery && installed.isEmpty) Given("a real full-Test archive and a native producer and requester in the same store")
      import org.goldenport.Consequence
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
      import org.goldenport.cncf.workflow.ContinuationIdentity
      import org.goldenport.cncf.processexecution.{SbtExecutionAttempt, SbtExecutionResult}
      import scala.jdk.CollectionConverters.*
      import java.util.concurrent.TimeUnit
      installed.foreach { case (executable, component) =>
        require(executable.isAbsolute && executable.normalize == executable && Files.isRegularFile(executable) && Files.isExecutable(executable),
          "Installed recovery requires an actual selected executable")
        require(component.isAbsolute && component.normalize == component && Files.isRegularFile(component),
          "Installed recovery requires the actual selected candidate Component CAR")
      }
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/recovery-v2-capacity").toAbsolutePath),
        "SPEC-").toRealPath()
      Files.createDirectories(root.resolve("capture-root/validation"))
      TypedSbtHostPlanFixture.build(root, successful = true, diagnosticBytes = 1500000)
      // Keep the actual full-Test ProductCheck and add an independently selected
      // ScalaTest suite for the third native check in this SPEC-owned repository.
      // Local fixtures use the current Test classpath prepared by Test/compile.
      // Installed qualification keeps the official primary runtime classpath.
      val localclasspath = Option.when(installed.isEmpty)(
        Path.of("target/sm-workflow/validation-classpath.txt").toAbsolutePath.normalize)
      TypedSbtHostPlanFixture.buildMetadata(root, selectedClasspath = localclasspath)
      val buildFile = root.resolve("repository/build.sbt")
      val build = Files.readString(buildFile).replace(root.resolve("sbt-executions.txt").toString,
        root.resolve("sbt-launches.txt").toString)
      Files.writeString(buildFile, build + "\nTest / test := (Test / runMain).toTask(\" ProductCheck\").value\n")
      Files.writeString(root.resolve("SPEC-review-fixture"), "SPECIFICATION-OWNED review qualification\n")
      if (delivery) Files.writeString(root.resolve("SPEC-delivery-fixture"), "SPEC requires actual managed Compile\n")
      val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
        GoalPhaseNativeValidation.Check("test", 2, WorkflowSbtValidation.Kind.Test),
        GoalPhaseNativeValidation.Check("test-only", 3, WorkflowSbtValidation.Kind.TestOnly))
      val initial = ThreeWorkflowNormalHostFixture.definition(root)
      val host = initial.copy(goal = initial.goal.copy(candidatePaths = Vector("src"),
        stepValidation = initial.goal.stepValidation.copy(checks = checks)))
      val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component",
        "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
        Vector.empty, NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original-authority", "1")), None, Vector.empty, "SPEC-only")
      val input = V.DelegateTaskInput("sm-workflow", "SPEC-legacy-v2-capacity", "SPEC-owned actual managed Test fixture",
        NonEmptyVector.one("src"), NonEmptyVector.of("compile", "test", "test-only"), NonEmptyVector.of("first", "second"),
        NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original-source", "1")), true, "code", "off", 10,
        "direct-instruction", Vector.empty, contract, if (delivery) Vector.empty else Vector("first"))
      var session = ""
      var intentRevision = 0L
      var association = Option.empty[WorkflowSbtCapture.Association]
      var actual = Option.empty[SbtExecutionResult]
      var actualCompile = Option.empty[SbtExecutionResult]
      var raw = Option.empty[WorkflowSbtCapture.ExternalReference]
      var legacyFailure = ""
      var oldHistory = Vector.empty[String]
      val artifactPath = "validation/SPEC-real-result.json"
      val artifactLimits = org.goldenport.cncf.resource.ResourceReadLimits(4 * 1024 * 1024)
      var currentParameters = BoundedWorkExecutionAdmissionFixture.logicalParameters ++ Map(
        "workflow.mechanical-receipt.max-bytes" -> "16777216",
        "textus.resource.url.file.roots" -> root.toString,
        "cncf.resource.read.max-bytes" -> "16777216")
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        if (delivery) prepare { setup => WorkflowManagedProcessExecution.repositoryValidationAttemptC(setup.core, 1, WorkflowSbtValidation.Kind.Compile)
          .flatMap {
            case SbtExecutionAttempt.Observed(result) => actualCompile = Some(result); Consequence.unit
            case _ => Consequence.stateConflict("SPEC typed managed Compile did not return an actual observation")
          } }
        def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
        val options = Map("input" -> "SPEC-input.json", "selection-id" -> "SPEC-v2-selection", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-v2-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        val selected = take(NativeDelegateTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC))
        prepare { setup =>
          val row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val started = read_value[V.DelegateTaskView](take(call("startDelegateTask", List(Property("input", input, None),
          Property("selectionId", "SPEC-v2-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-v2-key", None)))))
        session = started.sessionId
        val implement = read_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
        val implementationReference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-implementation", "1")
        val implementationExecution = V.WorkflowExecutionEvidence(Vector(implementationReference), Some("SPECIFICATION-OWNED-implementer"), None,
          Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPECIFICATION-OWNED-implementer", "ordinary")))
        val implementation = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED fixture implementation",
          Vector.empty, NonEmptyVector.of(V.DelegateTaskCondition("first", !delivery, "SPEC fixture selected"),
            V.DelegateTaskCondition("second", false, "SPEC delivery remains unevaluated")), Vector.empty, None, None)
        take(call("submitDelegateTaskWorkResult", List(Property("sessionId", session, None),
          Property("issuedWorkContext", implement.issuedWorkContext.get, None), Property("result", implementation, None),
          Property("resultReference", implementationReference, None), Property("executionEvidence", implementationExecution, None))))
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          completed <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC Start absent")))
          // Submit retains a completion, not a standalone checkpoint at its new revision.
          // Materialize the same completion transition as ordinary Advance before installing
          // this historical validation-intent fixture, without executing its checks.
          parts = completed.causation.value.stripPrefix("standalone-completed:").split(":", 2)
          _ <- if (completed.causation.value.startsWith("standalone-completed:") && parts.length == 2) Consequence.unit
            else Consequence.stateConflict("SPEC implementation completion absent")
          revision <- parts(0).toLongOption.map(Consequence.success)
            .getOrElse(Consequence.stateConflict("SPEC implementation source revision malformed"))
          prior <- DelegateTaskNativeRun.loadPayloadC(store, session, revision, setup.core)
          completions <- store.bindCompletionC(DelegateTaskNativeRun.completionOperation, DelegateTaskNativeRun.inputCodec,
            DelegateTaskNativeRun.resultCodec, DelegateTaskNativeRun.closingType, (_, _) => Consequence.success(Vector.empty))
          done <- completions.loadRetainedC(ContinuationIdentity(parts(1)))(using setup.core.executionContext)
            .flatMap(_.map(x => Consequence.success(x.completion.result)).getOrElse(Consequence.stateConflict("SPEC implementation result absent")))
          next <- StandaloneFixExecution.completedC(StandaloneFixExecution.Work(session, revision.toString, prior.state.stage, prior.state),
            done.result.value, done.resultReference)
          references <- DelegateTaskNativeRun.recordSemanticEvidenceC(h, setup.core, session, completed.revision.value,
            prior.state.stage, done.result.value)
          payload = prior.copy(state = next.copy(evidenceReferences = next.evidenceReferences ++ references), supersededWork = None,
            implementationWorker = done.evidence.workerIdentity,
            implementationWorkers = Some((prior.implementationWorkers.getOrElse(Vector.empty) ++ done.evidence.workerIdentity.toVector).distinct))
          current <- DelegateTaskNativeRun.saveC(h, store, completed, payload, setup.core)
          observed <- setup.observeC()
          empty = ProcessExecutionCapture(Vector.empty, 0L, false)
          prefixProcess = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), empty, empty, Vector.empty, 1L, "selected-sbt")
          prefixFacts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)
          compileFacts <- actualCompile.map(result => WorkflowSbtValidation.factsC(WorkflowSbtValidation.Kind.Compile, result))
            .getOrElse(Consequence.success(prefixFacts))
          compileProcess = actualCompile.map(_.processResult).getOrElse(prefixProcess)
          prefix = RepositorySyncNativeMergeValidation.Receipt("compile", WorkflowSbtCapture.termination(compileProcess),
            WorkflowSbtCapture.exitCode(compileProcess), WorkflowSbtCapture.text(compileProcess.stdout).value,
            WorkflowSbtCapture.text(compileProcess.stderr).value, Some(compileFacts), Some(compileProcess))
          a = WorkflowSbtCapture.Association(setup.core.component.get.core.instanceId.canonicalKey, session, current.revision.next.value,
            "participant", "test", 2, payload.root, payload.branch, payload.baseHead, observed.tree)
          reservation = BoundedWorkRuntime.ValidationCaptureReservation(a, "sm-workflow-validation-artifacts", artifactPath, artifactLimits.maximumBytes)
          candidate = StandaloneFixExecution.Candidate(observed.head, observed.tree, Vector.empty, "")
          state = payload.state.copy(stage = "ValidationIntent", candidate = Some(candidate), receipts = Vector(prefix),
            pendingCheck = Some("test"), automaticCount = 2, ordinaryCount = 1)
          saved <- DelegateTaskNativeRun.saveC(h, store, current, payload.copy(state = state, validationCaptureReservation = Some(reservation)), setup.core)
          _ = { intentRevision = saved.revision.value; association = Some(a); oldHistory = saved.history.map(_.causation.value) }
        } yield () }
        // Real execution is a typed managed Test. All source, direction and evidence here are SPEC-owned.
        prepare { setup => WorkflowManagedProcessExecution.repositoryValidationAttemptC(setup.core, 2, WorkflowSbtValidation.Kind.Test)
          .flatMap {
            case SbtExecutionAttempt.Observed(result) => actual = Some(result); Consequence.unit
            case _ => Consequence.stateConflict("SPEC typed managed Test did not return an actual observation")
          } }
        actual.get.processResult.stdout.byteCount should be > (1024L * 1024L)
        actual.get.processResult.termination shouldBe ProcessExecutionTermination.Exited(0)
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
        prepare { setup => for {
          facts <- WorkflowSbtValidation.factsC(WorkflowSbtValidation.Kind.Test, actual.get)
          ref <- WorkflowSbtCapture.archiveC(setup.core, "sm-workflow-validation-artifacts", artifactPath,
            association.get, facts, actual.get.processResult, artifactLimits)
          _ = raw = Some(ref)
        } yield () }
        // Simulate the historical v2 save at a finite 1 MiB mechanical bound; no DB edits or retry.
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
          small = h.copy(configuration = h.configuration.copy(mechanicalReceiptMaxBytes = Some(1048576)))
          normalStore <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          smallStore <- DelegateTaskNativeRun.storageC(small, definition, setup.core)
          instances <- normalStore.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC intent absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(normalStore, session, intentRevision, setup.core)
          facts <- WorkflowSbtValidation.factsC(WorkflowSbtValidation.Kind.Test, actual.get)
          process = actual.get.processResult
          receipt = RepositorySyncNativeMergeValidation.Receipt("test", WorkflowSbtCapture.termination(process),
            WorkflowSbtCapture.exitCode(process), WorkflowSbtCapture.text(process.stdout).value,
            WorkflowSbtCapture.text(process.stderr).value, Some(facts), Some(process))
          state <- StandaloneFixExecution.observedC(payload.state, receipt)
          failed = DelegateTaskNativeRun.saveC(small, smallStore, current, payload.copy(state = state), setup.core)
          _ = legacyFailure = failed.toString
          _ <- if (failed.isFaillure) Consequence.unit else Consequence.stateConflict("SPEC legacy v2 save unexpectedly fit")
          after <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
          _ <- if (after.exists(_.revision.value == intentRevision)) Consequence.unit
            else Consequence.stateConflict("SPEC failed save advanced the native frontier")
        } yield () }
        legacyFailure should include("Mechanical receipt encoding")
      }
      val countBefore = Files.readString(root.resolve("sbt-executions.txt"))
      var sequence = 0
      def installedRecord(service: String, operation: String, arguments: Vector[String]): Record = {
        val selectedComponent = installed.map(_._2).getOrElse(fail("No candidate Component CAR was selected"))
        val logs = Files.createDirectories(root.resolve("installed-recovery-logs"))
        sequence += 1
        val stdout = logs.resolve(sequence + ".stdout.json")
        val stderr = logs.resolve(sequence + ".stderr")
        val argv = NativeWorkflowCliFixture.candidateCommand(selectedComponent) ++ Vector(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name +
          "." + service + "." + operation, "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"),
          "--textus.test.descriptor=" + root.resolve(".textus/test.yaml")) ++
          arguments ++ Vector("--json")
        val process = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
        try {
          process.getOutputStream.close()
          withClue("Installed candidate operation timed out; stderr=" + stderr) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
          withClue("Installed candidate operation failed; stderr=" + Files.readString(stderr)) { process.exitValue() shouldBe 0 }
          take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(stdout)))
        } finally {
          if (process.isAlive) { process.destroy(); if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly() }
          process.getInputStream.close(); process.getErrorStream.close()
        }
      }
      def installedCall[A: org.goldenport.convert.ValueReader](operation: String, value: org.goldenport.record.RecordPresentable): A =
        take(summon[org.goldenport.convert.ValueReader[A]].readC(installedRecord("DelegateTaskService", operation,
          Vector("--input", take(value.toRecord().toJsonStringC)))))
      val described = installed match {
        case Some(_) => installedCall[V.DelegateTaskValidationRecoveryView]("describeDelegateTaskValidationRecovery",
          V.DelegateTaskValidationRecoveryDescriptionInput(session))
        case None =>
          var result = Option.empty[V.DelegateTaskValidationRecoveryView]
          _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (owner, component, actor, _) =>
            val response = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
              operation = "describeDelegateTaskValidationRecovery", properties = List(Property("input",
                V.DelegateTaskValidationRecoveryDescriptionInput(session), None))), actor)
            result = Some(read_value[V.DelegateTaskValidationRecoveryView](take(response)))
          }
          result.get
      }
      val target = take(DelegateTaskValidationRecovery.targetC(described.target))
      target.captureAvailability shouldBe "available"
      target.frontierRevision shouldBe intentRevision
      target.successfulPrefix shouldBe Vector("compile")
      val proof = DelegateTaskValidationRecovery.Proof("SPEC-actual-terminal-event", "SPEC-completed-caller", "exited",
        Some(0), "completed", 0, "host-attested", "available", "unknown")
      val reference = ContextReference("SPEC-independent-host-observation", "1")
      val binding = take(DelegateTaskValidationRecovery.bindingC(target, proof, "participant", reference))
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, prepare) =>
        prepare(_.evidenceC(NativeExternalEvidence.Purpose.DelegateTaskValidationRecovery,
          ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision), reference, "participant", binding))
      }
      val recovery = V.DelegateTaskValidationRecoveryInput(session, described.target, proof.asJson.noSpaces, "participant",
        V.WorkflowArtifactReference(reference.identity, reference.revision))
      val collected = installed match {
        case Some(_) => installedCall[V.DelegateTaskValidationRecoveryView]("recoverDelegateTaskValidation", recovery)
        case None =>
          var result = Option.empty[V.DelegateTaskValidationRecoveryView]
          _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (owner, component, actor, _) =>
            val response = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
              operation = "recoverDelegateTaskValidation", properties = List(Property("input", recovery, None))), actor)
            result = Some(read_value[V.DelegateTaskValidationRecoveryView](take(response)))
          }
          result.get
      }
      collected.outcome shouldBe "actual-result-collected"
      Files.readString(root.resolve("sbt-executions.txt")) shouldBe countBefore
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, prepare) =>
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC collected Session absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, setup.core)
          restored <- WorkflowSbtCapture.hydrateC(setup.core, raw.get, association.get,
            payload.state.receipts.last.sbt.get, artifactLimits)
          _ <- if (current.revision.value == intentRevision + 1 && current.history.map(_.causation.value).take(oldHistory.size) == oldHistory &&
            payload.root == root.resolve("repository").toString && payload.branch == "main" && payload.baseHead == association.get.baseHead &&
            payload.state.receipts.map(_.identity) == Vector("compile", "test") && payload.state.automaticCount == 2 &&
            payload.state.ordinaryCount == 1 && payload.state.input.conditions == Vector("first", "second") &&
            payload.state.receipts.last.processObservation.contains(actual.get.processResult) && restored == actual.get.processResult)
            Consequence.unit else Consequence.stateConflict("SPEC physical reopen crossed native history or actual bytes")
        } yield () }
        Files.readString(root.resolve("sbt-executions.txt")) shouldBe countBefore
      }
      // Public preparation consumes the actual issued Review, including its v3
      // receipt after public Record projection. The SPEC host provisions the
      // passive result independently before the separate public Submit.
      def publicCall(service: String, operation: String, properties: List[Property], args: Vector[String]): Record = installed match {
        case Some(_) => installedRecord(service, operation, args)
        case None =>
          var result = Option.empty[Record]
          _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (owner, component, actor, _) =>
            take(owner.executeOperationResponseInContext(Request.of(component = component.name, service = service,
              operation = operation, properties = properties), actor)) match {
              case org.goldenport.protocol.operation.OperationResponse.RecordResponse(record) => result = Some(record)
              case org.goldenport.protocol.operation.OperationResponse.Json(json) =>
                result = Some(take(new org.goldenport.record.io.RecordDecoder().json(json.noSpaces)))
              case other => fail("SPEC public operation did not return a Record: " + other)
            }
          }
          result.get
      }
      def advance(): V.DelegateTaskView = {
        val record = publicCall("DelegateTaskService", "advanceDelegateTask", List(Property("sessionId", session, None)),
          Vector("--sessionId", session))
        val control = take(summon[org.goldenport.convert.ValueReader[V.DelegateTaskView]].readC(record))
        control.retainedWork.map { reference =>
          val input = take(reference.toRecord().toJsonStringC)
          val retained = publicCall(WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
            List(Property("input", reference, None)), Vector("--input", input))
          val detail = take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedWorkReadResult]].readC(retained))
          take(V.DelegateTaskView.createC(detail.response))
        }.getOrElse(control)
      }
      var review = advance()
      var remaining = 8
      while (review.reviewContext.isEmpty && remaining > 0) { review = advance(); remaining -= 1 }
      withClue("SPEC actual Review frontier: " + review.message) { review.reviewContext should not be empty }
      review.sessionId shouldBe session
      val reviewJson = io.circe.parser.parse(take(review.toRecord().toJsonStringC)).toOption.get
      reviewJson.noSpaces should include(WorkflowSbtCapture.externalReceiptSchema)
      reviewJson.noSpaces should include(raw.get.contentSha256)
      val reviewFile = root.resolve("SPEC-review-request.json")
      Files.writeString(reviewFile, reviewJson.noSpaces)
      val result = V.DelegateTaskWorkResult("accept", "SPECIFICATION-OWNED independent review of retained observations",
        Vector.empty, NonEmptyVector.of(V.DelegateTaskCondition("first", !delivery, "SPEC retained selected evidence reviewed"),
          V.DelegateTaskCondition("second", false, "SPEC later delivery remains unevaluated")), Vector.empty, None, None)
      val reviewReference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-independent-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reviewReference), Some("SPECIFICATION-OWNED-reviewer"), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPECIFICATION-OWNED-reviewer", "ordinary")))
      val crossedReference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-crossed-review", "1")
      val crossedExecution = V.WorkflowExecutionEvidence(Vector(crossedReference), Some("SPECIFICATION-OWNED-reviewer"), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPECIFICATION-OWNED-implementer", "ordinary")))
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (owner, component, actor, provision) =>
        provision { setup => for {
          row <- org.simplemodeling.textus.smworkflow.preparation.NativeReviewPreparation.delegateC(review.reviewContext.get,
            result, crossedReference, crossedExecution, "normal-development-host", setup.executionContext.clock.instant().minusSeconds(60))
          _ <- setup.createC("host_evidence", row.recordIdentity, row.record)
        } yield () }
        val refused = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
          operation = "submitDelegateTaskWorkResult", properties = List(Property("sessionId", session, None),
            Property("issuedWorkContext", review.issuedWorkContext.get, None), Property("result", result, None),
            Property("resultReference", crossedReference, None), Property("executionEvidence", crossedExecution, None))), actor)
        refused.isSuccess shouldBe false
      }
      def write(name: String, value: org.goldenport.record.RecordPresentable): String = {
        val file = root.resolve(name)
        Files.writeString(file, take(value.toRecord().toJsonStringC))
        file.toUri.toASCIIString
      }
      var reviewOccurredAt = ""
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, provision) =>
        provision { setup =>
          reviewOccurredAt = setup.executionContext.clock.instant().minusSeconds(60).toString
          Consequence.unit
        }
      }
      val preparationInput = V.WorkflowEvidencePreparationInput("DelegateTask", "review", reviewFile.toUri.toASCIIString,
        write("SPEC-review-result.json", result), write("SPEC-review-reference.json", reviewReference),
        write("SPEC-review-execution.json", execution), "normal-development-host",
        reviewOccurredAt, "entity:smworkflow.cli.host_evidence", None)
      val prepared = publicCall("NativeWorkflowPreparationService", "prepareEvidence",
        List(Property("input", preparationInput, None), Property("preparationFormat", "legacy-full", None)),
        Vector("--input", take(preparationInput.toRecord().toJsonStringC), "--preparationFormat", "legacy-full"))
      val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, provision) =>
        provision(_.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
      }
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (owner, component, actor, _) =>
        val foreign = reviewReference.copy(identity = "SPECIFICATION-OWNED-foreign-review")
        val refused = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
          operation = "submitDelegateTaskWorkResult", properties = List(Property("sessionId", session, None),
            Property("issuedWorkContext", review.issuedWorkContext.get, None), Property("result", result, None),
            Property("resultReference", foreign, None), Property("executionEvidence", execution, None))), actor)
        refused.isSuccess shouldBe false
      }
      val properties = List(Property("sessionId", session, None), Property("issuedWorkContext", review.issuedWorkContext.get, None),
        Property("result", result, None), Property("resultReference", reviewReference, None), Property("executionEvidence", execution, None))
      def json(value: org.goldenport.record.RecordPresentable) = take(value.toRecord().toJsonStringC)
      publicCall("DelegateTaskService", "submitDelegateTaskWorkResult", properties,
        Vector("--sessionId", session, "--issuedWorkContext", json(review.issuedWorkContext.get), "--result", json(result),
          "--resultReference", json(reviewReference), "--executionEvidence", json(execution)))
      val next = advance()
      next.sessionId shouldBe session
      Files.readString(root.resolve("sbt-executions.txt")) shouldBe countBefore
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, provision) =>
        provision { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC reviewed Session absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, setup.core)
          receipt <- payload.state.receipts.find(_.identity == "test").map(Consequence.success)
            .getOrElse(Consequence.stateConflict("SPEC full Test receipt absent"))
          reopened <- WorkflowSbtCapture.hydrateC(setup.core, raw.get, association.get, receipt.sbt.get, artifactLimits)
          _ <- if (receipt.externalCapture.contains(raw.get) && payload.root == association.get.root &&
            payload.branch == association.get.branch && payload.baseHead == association.get.baseHead &&
            payload.state.reviewReference.contains(ContextReference(reviewReference.identity, reviewReference.revision)) &&
            reopened == actual.get.processResult && WorkflowSbtCapture.text(reopened.stdout).value.contains("PRODUCT_CHECK_PASSED"))
            Consequence.unit else Consequence.stateConflict("SPEC reopened review crossed receipt, source or actual full-Test evidence")
        } yield () }
      }
      Files.readString(root.resolve("sbt-executions.txt")) shouldBe countBefore
      if (delivery) {
        def materialize(control: V.DelegateTaskView): V.DelegateTaskView = {
          val base = control.retainedWork.map { ref =>
            val record = publicCall(WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
              List(Property("input", ref, None)), Vector("--input", json(ref)))
            take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedWorkReadResult]].readC(record)).response
          }.getOrElse(control.toRecord())
          val expanded = control.retainedMaterials.foldLeft(base) { (prior, field) =>
            val record = publicCall(WorkflowRetainedWork.serviceName, WorkflowRetainedSnapshot.readOperation,
              List(Property("input", field.reference, None)), Vector("--input", json(field.reference)))
            val detail = take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedSnapshotReadResult]].readC(record))
            val overlay = control.controlSummary.flatMap(_.getRecord(field.fieldName)).getOrElse(Record.empty)
            val material = Record.createFull(detail.material.fields.filterNot(f => overlay.getAny(f.key).nonEmpty)
              .map(f => f.key -> f.value.single)) ++ overlay
            Record.createFull(prior.fields.filterNot(_.key == field.fieldName).map(f => f.key -> f.value.single)) ++
              Record.data(field.fieldName -> material)
          }
          take(V.DelegateTaskView.createC(expanded))
        }
        def advanceFull(id: String): V.DelegateTaskView = materialize(take(summon[org.goldenport.convert.ValueReader[V.DelegateTaskView]].readC(
          publicCall("DelegateTaskService", "advanceDelegateTask", List(Property("sessionId", id, None)), Vector("--sessionId", id)))))
        var finalView = materialize(next)
        var budget = 12
        while (finalView.kind != "Return" && finalView.work.isEmpty && budget > 0) {
          finalView = advanceFull(session); budget -= 1
        }
        if (finalView.work.nonEmpty) {
          val work = parser.parse(take(finalView.work.get.toJsonStringC)).toOption.get
          work.hcursor.get[String]("kind").toOption shouldBe Some("DeliveryAcceptance")
          val finalResult = V.DelegateTaskWorkResult("accept", "SPEC evaluates retained checks and actual commit",
            Vector.empty, NonEmptyVector.of(V.DelegateTaskCondition("first", true, "SPEC retained actual check"),
              V.DelegateTaskCondition("second", true, "SPEC retained actual source")), Vector.empty, None, None)
          val finalRef = V.WorkflowArtifactReference("SPEC-delivery-acceptance", "1")
          val finalExecution = V.WorkflowExecutionEvidence(Vector(finalRef), Some("SPEC-acceptance-worker"), None,
            Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPEC-acceptance-worker", "ordinary")))
          publicCall("DelegateTaskService", "submitDelegateTaskWorkResult", List(Property("sessionId", session, None),
            Property("issuedWorkContext", finalView.issuedWorkContext.get, None), Property("result", finalResult, None),
            Property("resultReference", finalRef, None), Property("executionEvidence", finalExecution, None)),
            Vector("--sessionId", session, "--issuedWorkContext", json(finalView.issuedWorkContext.get),
              "--result", json(finalResult), "--resultReference", json(finalRef), "--executionEvidence", json(finalExecution)))
          finalView = advanceFull(session)
        }
        finalView.kind shouldBe "Return"
        val returnedJson = parser.parse(take(finalView.state.get.toJsonStringC)).toOption.get
        val producer = take(GoalPhaseNativeMetadata.normalizePublicJson(returnedJson)
          .flatMap(_.as[StandaloneFixExecution.State].left.map(_.message)).fold(Consequence.stateConflict(_), Consequence.success))
        val a = association.get
        val policy = Json.obj("schemaVersion" -> "sm-workflow.delegate-task-returned-capture-read.v1".asJson,
          "bindings" -> Json.arr(Json.obj("sourceComponentInstance" -> a.component.asJson, "sourceSessionId" -> session.asJson,
            "sourceRepository" -> producer.input.repositoryIdentity.asJson, "sourceParticipant" -> producer.participant.asJson,
            "sourceRoot" -> a.root.asJson, "sourceBranch" -> a.branch.asJson, "sourceBaseHead" -> a.baseHead.asJson,
            "sourceCandidateTree" -> a.candidateTree.asJson, "sourceCommit" -> producer.commit.get.identity.asJson,
            "sourceWorkspace" -> raw.get.workspace.asJson, "readWorkspace" -> "spec-returned-capture-read".asJson,
            "maximumBytes" -> artifactLimits.maximumBytes.asJson)))
        val key = "sm-workflow.delegate-task.returned-capture-read-bindings"
        currentParameters = currentParameters + (key -> policy.noSpaces)
        if (installed.nonEmpty) {
          val file = root.resolve(".textus/config.json")
          val config = parser.parse(Files.readString(file)).toOption.get
          Files.writeString(file, config.mapObject(_.add(key, policy)).noSpaces)
        }
        val requesterInput = input.copy(delegation = input.delegation.copy(role = "requester"))
        val requesterFile = root.resolve("SPEC-requester-input.json")
        Files.writeString(requesterFile, json(requesterInput))
        val startPrep = V.WorkflowDelegateTaskStartPreparationInput(requesterFile.toUri.toASCIIString,
          "SPEC-requester-selection", "participant", "ExplicitHuman", "SPEC-requester-key", "entity:smworkflow.cli.goal_selection")
        val preparedStart = publicCall("NativeWorkflowPreparationService", "prepareDelegateTaskStart",
          List(Property("input", startPrep, None), Property("preparationFormat", "legacy-full", None)),
          Vector("--input", json(startPrep), "--preparationFormat", "legacy-full"))
        val startRow = preparedStart.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, provision) =>
          provision(_.createC("goal_selection", startRow.getString("id").get, startRow.removeKeys(Set("id"))))
        }
        val startProps = List(Property("input", requesterInput, None), Property("selectionId", "SPEC-requester-selection", None),
          Property("participant", "participant", None), Property("idempotencyKey", "SPEC-requester-key", None))
        val requester = materialize(take(summon[org.goldenport.convert.ValueReader[V.DelegateTaskView]].readC(
          publicCall("DelegateTaskService", "startDelegateTask", startProps, Vector("--input", json(requesterInput),
            "--selectionId", "SPEC-requester-selection", "--participant", "participant", "--idempotencyKey", "SPEC-requester-key")))))
        var producerrevision = 0L
        if (installed.isEmpty) _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, provision) =>
          provision(_.delegateCurrentC(InstanceIdentity(session)).map { current => producerrevision = current.revision.value; () })
        }
        def observation(event: String, result: Option[Record] = None, selection: Option[Record] = None) =
          V.DelegateTaskObservationInput(requester.sessionId, event, V.WorkflowArtifactReference(
            if (installed.isEmpty && event == "result-received") session + ":standalone-state:" + producerrevision else "SPEC-" + event, "1"),
            Some(session), result, selection, "SPEC actual requester observation")
        def observe(value: V.DelegateTaskObservationInput): V.DelegateTaskView = materialize(take(
          summon[org.goldenport.convert.ValueReader[V.DelegateTaskView]].readC(publicCall("DelegateTaskDeliveryService",
            "observeDelegateTaskDelivery", List(Property("input", value, None)), Vector("--input", json(value))))))
        observe(observation("dispatch-observed"))
        def refused(parameters: Map[String, String], value: V.DelegateTaskObservationInput): Unit =
          _with_runtime_(root, "Equal", Some(host), parameters, componentInstance = "default") { (owner, component, actor, _) =>
            owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskDeliveryService",
              operation = "observeDelegateTaskDelivery", properties = List(Property("input", value, None))), actor).isSuccess shouldBe false
          }
        val receive = observation("result-received", finalView.state)
        refused(currentParameters - key, receive)
        def crossedReceipt(field: String, replacement: Json): V.DelegateTaskObservationInput = {
          val changed = producer.asJson.mapObject(_.add("receipts", producer.asJson.hcursor.get[Vector[Json]]("receipts").toOption.get.map { receipt =>
            receipt.hcursor.get[Json]("externalCapture").toOption match {
              case None => receipt
              case Some(ref) =>
                val altered = if (field == "contentSha256") ref.mapObject(_.add(field, replacement))
                  else ref.mapObject(_.add("association", ref.hcursor.get[Json]("association").toOption.get.mapObject(_.add(field, replacement))))
                receipt.mapObject(_.add("externalCapture", altered))
            }
          }.asJson))
          receive.copy(result = Some(take(new org.goldenport.record.io.RecordDecoder().json(changed.noSpaces))))
        }
        Vector("component", "session", "participant", "root", "branch", "baseHead", "candidateTree", "check")
          .foreach(field => refused(currentParameters, crossedReceipt(field, "SPEC-foreign".asJson)))
        refused(currentParameters, crossedReceipt("ordinal", 999.asJson))
        refused(currentParameters, crossedReceipt("contentSha256", ("0" * 64).asJson))
        val foreignFields = Vector("sourceComponentInstance", "sourceSessionId", "sourceRepository", "sourceParticipant",
          "sourceRoot", "sourceBranch", "sourceBaseHead", "sourceCandidateTree", "sourceCommit", "sourceWorkspace", "readWorkspace")
        foreignFields.foreach { field =>
          val crossed = policy.mapObject(_.add("bindings", policy.hcursor.get[Vector[Json]]("bindings").toOption.get.map(
            _.mapObject(_.add(field, "SPEC-foreign".asJson))).asJson))
          refused(currentParameters + (key -> crossed.noSpaces), receive)
        }
        val small = policy.mapObject(_.add("bindings", policy.hcursor.get[Vector[Json]]("bindings").toOption.get.map(
          _.mapObject(_.add("maximumBytes", 1.asJson))).asJson))
        refused(currentParameters + (key -> small.noSpaces), receive)
        val duplicate = policy.mapObject(_.add("bindings", Json.arr(policy.hcursor.downField("bindings").downN(0).focus.get,
          policy.hcursor.downField("bindings").downN(0).focus.get)))
        refused(currentParameters + (key -> duplicate.noSpaces), receive)
        val artifact = root.resolve("capture-root").resolve(artifactPath)
        val bytes = Files.readAllBytes(artifact)
        try {
          Files.delete(artifact)
          refused(currentParameters, receive)
          Files.write(artifact, bytes.take(bytes.length / 2))
          refused(currentParameters, receive)
          Files.write(artifact, bytes.updated(bytes.length - 1, (bytes.last ^ 1).toByte))
          refused(currentParameters, receive)
        } finally Files.write(artifact, bytes)
        refused(currentParameters, receive.copy(destinationSessionId = Some("SPEC-foreign-session")))
        val received = observe(observation("result-received", finalView.state))
        received.kind shouldBe "Wait"
        if (installed.isEmpty) _with_runtime_(root, "Equal", Some(host), currentParameters - key, componentInstance = "default") { (_, _, _, provision) =>
          provision { setup => for {
            current <- setup.delegateCurrentC(InstanceIdentity(requester.sessionId))
            h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
              "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
            store <- DelegateTaskNativeRun.storageC(h, current.definition, setup.core)
            _ = When("the requester reopens and its native consumer resolves the actual producer checkpoint without a cross-workspace binding")
            requesterstate <- DelegateTaskNativeRun.loadPayloadC(store, requester.sessionId, current.revision.value, setup.core)
            native <- ManagedDevelopmentBoundedWork.receivedC(store, current.definition, requesterstate.state, setup.core)
            _ = {
              Then("the same-store consumer reloads the genuine producer checkpoint and its archived successful Test without a cross-workspace binding")
              native.state.receipts.exists(r => r.externalCapture.nonEmpty && r.processObservation.nonEmpty &&
                RepositorySyncNativeMergeValidation.productSuccessful(r)) shouldBe true
              native.state.commit shouldBe producer.commit
              requesterstate.state.stage shouldBe "ResultReceived"
            }
          } yield () }
        }
        val context = take(summon[org.goldenport.convert.ValueReader[V.DelegateTaskAdoptionContext]].readC(
          publicCall("DelegateTaskDeliveryService", "describeDelegateTaskAdoption", List(Property("sessionId", requester.sessionId, None)),
            Vector("--sessionId", requester.sessionId))))
        val selection = Record.data("sourceCommit" -> producer.commit.get.identity, "coordinates" -> Vector.empty[String],
          "selectionReference" -> Record.data("identity" -> "SPEC-selection", "revision" -> "1"),
          "consumerCheckReferences" -> Vector.empty[Record])
        val adopt = observation("adopt", selection = Some(selection))
        val execution = V.WorkflowExecutionEvidence(Vector(adopt.sourceReference), Some("participant"), None, None)
        var occurred = ""
        _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, provision) =>
          provision { setup => occurred = setup.executionContext.clock.instant().minusSeconds(1).toString; Consequence.unit }
        }
        val prep = V.WorkflowEvidencePreparationInput("DelegateTask", "adoption", write("SPEC-adoption-context.json", context),
          write("SPEC-adoption-input.json", adopt), write("SPEC-adoption-reference.json", adopt.sourceReference),
          write("SPEC-adoption-execution.json", execution), "normal-development-host", occurred,
          "entity:smworkflow.cli.host_evidence", None)
        val preparedAdoption = publicCall("NativeWorkflowPreparationService", "prepareEvidence",
          List(Property("input", prep, None), Property("preparationFormat", "legacy-full", None)),
          Vector("--input", json(prep), "--preparationFormat", "legacy-full"))
        val row = preparedAdoption.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (_, _, _, provision) =>
          provision(_.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
        }
        observe(adopt).kind shouldBe "Terminal"
        advanceFull(requester.sessionId).kind shouldBe "Terminal"
        Files.readString(root.resolve("sbt-executions.txt")) shouldBe countBefore
      }
  }

  "The managed lost-result boundary" should {
    "start one new full Test after an independently attested ended output-limited lost result" in {
      import org.goldenport.Consequence
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
      import org.goldenport.cncf.processexecution.{SbtExecutionAttempt, SbtExecutionResult}
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/recovery-new-test").toAbsolutePath),
        "SPEC-").toRealPath()
      Files.createDirectories(root.resolve("capture-root"))
      TypedSbtHostPlanFixture.build(root, successful = true)
      Files.writeString(root.resolve("repository/src/test/scala/ProductCheck.scala"), """object ProductCheck {
        |  def main(args: Array[String]): Unit = {
        |    val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
        |    java.nio.file.Files.write(root.getParent.resolve("sbt-executions.txt"),
        |      (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
        |      java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
        |    println("SPEC_ACTUAL_FULL_TEST_BEGIN")
        |    print("D" * (4 * 1024 * 1024)); System.out.flush()
        |    assert(java.nio.file.Files.readString(root.resolve("value.txt")) == "expected\n")
        |    println("SPEC_ACTUAL_FULL_TEST_PASSED")
        |  }
        |}
        |""".stripMargin)
      val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
        GoalPhaseNativeValidation.Check("test", 2, WorkflowSbtValidation.Kind.Test),
        GoalPhaseNativeValidation.Check("test-only", 3, WorkflowSbtValidation.Kind.TestOnly))
      val initial = ThreeWorkflowNormalHostFixture.definition(root)
      val host = initial.copy(goal = initial.goal.copy(candidatePaths = Vector("src"),
        stepValidation = initial.goal.stepValidation.copy(checks = checks)))
      val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component",
        "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
        Vector.empty, NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original-authority", "1")), None, Vector.empty, "SPEC-only")
      val input = V.DelegateTaskInput("sm-workflow", "SPEC-legacy-lost-new-test", "SPEC-owned lost-result fixture",
        NonEmptyVector.one("src"), NonEmptyVector.of("compile", "test", "test-only"), NonEmptyVector.of("first", "second"),
        NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original-source", "1")), true, "code", "off", 10,
        "direct-instruction", Vector.empty, contract, Vector("first"))
      val parameters = BoundedWorkExecutionAdmissionFixture.parameters ++ Map("workflow.mechanical-receipt.max-bytes" -> "67108864")
      var session = ""
      var oldRevision = 0L
      var oldHistory = Vector.empty[String]
      var oldResult = Option.empty[SbtExecutionResult]
      var oldCandidate = Option.empty[StandaloneFixExecution.Candidate]
      var oldBase = ""
      _with_runtime_(root, "Equal", Some(host), parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
        val options = Map("input" -> "SPEC-input.json", "selection-id" -> "SPEC-lost-selection", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-lost-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        val selected = take(NativeDelegateTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC))
        prepare { setup =>
          val row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val started = read_value[V.DelegateTaskView](take(call("startDelegateTask", List(Property("input", input, None),
          Property("selectionId", "SPEC-lost-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-lost-key", None)))))
        session = started.sessionId
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC Start absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, setup.core)
          observed <- setup.observeC()
          empty = ProcessExecutionCapture(Vector.empty, 0L, false)
          process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), empty, empty, Vector.empty, 1L, "selected-sbt")
          facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)
          prefix = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "", "", Some(facts), Some(process))
          candidate = StandaloneFixExecution.Candidate(observed.head, observed.tree, Vector.empty, "")
          state = payload.state.copy(stage = "ValidationIntent", selected = checks.take(2), candidate = Some(candidate), receipts = Vector(prefix),
            pendingCheck = Some("test"), automaticCount = 2, ordinaryCount = 1)
          saved <- DelegateTaskNativeRun.saveC(h, store, current, payload.copy(state = state), setup.core)
          _ = { oldRevision = saved.revision.value; oldHistory = saved.history.map(_.causation.value)
            oldCandidate = Some(candidate); oldBase = payload.baseHead }
        } yield () }
        prepare { setup => WorkflowManagedProcessExecution.repositoryValidationAttemptC(setup.core, 2, WorkflowSbtValidation.Kind.Test)
          .flatMap {
            case SbtExecutionAttempt.Observed(result) => oldResult = Some(result); Consequence.unit
            case _ => Consequence.stateConflict("SPEC old typed Test did not return an observation")
          } }
        oldResult.get.processResult.termination shouldBe ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)
        oldResult.get.processResult.stdout.truncated shouldBe true
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
        // Exercise the old v2 result save, without preserving this test-owned raw capture as a recovery artifact.
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
          small = h.copy(configuration = h.configuration.copy(mechanicalReceiptMaxBytes = Some(1048576)))
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          smallStore <- DelegateTaskNativeRun.storageC(small, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC old intent absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session, oldRevision, setup.core)
          facts <- WorkflowSbtValidation.factsC(WorkflowSbtValidation.Kind.Test, oldResult.get)
          process = oldResult.get.processResult
          receipt = RepositorySyncNativeMergeValidation.Receipt("test", WorkflowSbtCapture.termination(process),
            WorkflowSbtCapture.exitCode(process), WorkflowSbtCapture.text(process.stdout).value,
            WorkflowSbtCapture.text(process.stderr).value, Some(facts), Some(process))
          state <- StandaloneFixExecution.observedC(payload.state, receipt)
          refused = DelegateTaskNativeRun.saveC(small, smallStore, current, payload.copy(state = state), setup.core)
          _ <- if (refused.isFaillure && refused.toString.contains("Mechanical receipt encoding")) Consequence.unit
            else Consequence.stateConflict("SPEC old v2 capacity refusal was not observed")
          after <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
          _ <- if (after.exists(_.revision.value == oldRevision)) Consequence.unit
            else Consequence.stateConflict("SPEC old failed save advanced the intent")
        } yield () }
      }
      val oldMarker = Files.readString(root.resolve("sbt-executions.txt"))
      // Drop this test caller's in-memory observation; only the native intent and
      // the separately supplied SPEC terminal attestation survive the reopen.
      oldResult = None
      Files.writeString(root.resolve("SPEC-new-capture-policy"), "SPEC selects finite 8 MiB stdout for the new host\n")
      _with_runtime_(root, "Equal", Some(host), parameters) { (owner, component, actor, prepare) =>
        def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
        val described = read_value[V.DelegateTaskValidationRecoveryView](take(call("describeDelegateTaskValidationRecovery",
          List(Property("input", V.DelegateTaskValidationRecoveryDescriptionInput(session), None)))))
        val target = take(DelegateTaskValidationRecovery.targetC(described.target))
        target.captureAvailability shouldBe "not-retained"
        target.frontierRevision shouldBe oldRevision
        target.successfulPrefix shouldBe Vector("compile")
        // This SPEC caller returned normally after reporting the incomplete attempt; no OS exit is invented.
        val proof = DelegateTaskValidationRecovery.Proof("SPEC-actual-output-limit-event", "SPEC-ended-caller",
          "output-limit-exceeded", None, "completed", 0, "host-attested", "unavailable", "unknown")
        val reference = ContextReference("SPEC-independent-ended-observation", "1")
        val binding = take(DelegateTaskValidationRecovery.bindingC(target, proof, "participant", reference))
        prepare(_.evidenceC(NativeExternalEvidence.Purpose.DelegateTaskValidationRecovery,
          ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision), reference, "participant", binding))
        val recovery = V.DelegateTaskValidationRecoveryInput(session, described.target, proof.asJson.noSpaces, "participant",
          V.WorkflowArtifactReference(reference.identity, reference.revision))
        val admitted = read_value[V.DelegateTaskValidationRecoveryView](take(call("recoverDelegateTaskValidation", List(Property("input", recovery, None)))))
        admitted.outcome shouldBe "authorized-awaiting-advance"
        Files.readString(root.resolve("sbt-executions.txt")) shouldBe oldMarker
        When("a separate ordinary Advance persists the one recovered full-Test result and reaches later code acceptance")
        val advanced = call("advanceDelegateTask", List(Property("sessionId", session, None)))
        Then("later code acceptance refuses the missing focused execution while the successful recovered full Test remains retained")
        advanced.isFaillure shouldBe true
        advanced.toString should include("Code admission requires compilation and actually executed nonempty focused behavioral tests")
        val markers = Files.readString(root.resolve("sbt-executions.txt")).linesIterator.toVector
        markers.size shouldBe 2
        markers.head shouldBe oldMarker.trim
        markers.distinct.size shouldBe 2
      }
      _with_runtime_(root, "Equal", Some(host), parameters) { (_, _, _, prepare) =>
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_.copy(mechanicalReceiptMaxBytes = Some(67108864)),
            _collection_("instances"), _collection_("continuations"), "sm-workflow", root.resolve("repository").toString,
            "main", Vector("src"), checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC new-result Session absent")))
          old <- DelegateTaskNativeRun.loadPayloadC(store, session, oldRevision, setup.core)
          intent <- DelegateTaskNativeRun.loadPayloadC(store, session, oldRevision + 2, setup.core)
          result <- DelegateTaskNativeRun.loadPayloadC(store, session, oldRevision + 3, setup.core)
          newReceipt <- result.state.receipts.lastOption.map(Consequence.success)
            .getOrElse(Consequence.stateConflict("SPEC new result absent"))
          process <- newReceipt.processObservation.map(Consequence.success)
            .getOrElse(Consequence.stateConflict("SPEC new actual capture not hydrated"))
          _ <- if (old.state.stage == "ValidationIntent" && old.state.pendingCheck.contains("test") &&
            old.validationCaptureReservation.isEmpty && current.history.map(_.causation.value).take(oldHistory.size) == oldHistory &&
            intent.state.stage == "ValidationIntent" && intent.validationCaptureReservation.exists(_.association.intentRevision == oldRevision + 2) &&
            result.state.receipts.map(_.identity) == Vector("compile", "test") &&
            result.state.receipts.head == old.state.receipts.head && result.state.candidate == oldCandidate &&
            result.root == root.resolve("repository").toString && result.branch == "main" && result.baseHead == oldBase &&
            result.state.input == old.state.input && result.state.input.conditions == Vector("first", "second") &&
            result.state.automaticCount == 2 && result.state.ordinaryCount == 1 &&
            newReceipt.externalCapture.nonEmpty && process.termination == ProcessExecutionTermination.Exited(0) &&
            process.stdout.byteCount > 4L * 1024 * 1024 &&
            WorkflowSbtCapture.text(process.stdout).value.contains("SPEC_ACTUAL_FULL_TEST_PASSED") &&
            RepositorySyncNativeMergeValidation.productSuccessful(newReceipt)) Consequence.unit
            else Consequence.stateConflict("SPEC reopened result crossed intent, history, source, conditions or actual output")
        } yield () }
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 2
      }
    }
    "collect an actual managed Test artifact after a legacy v2 save exceeds its finite SQLite receipt bound" in {
      qualifyAvailableRecovery(None)
    }
    "retain the same native Session, successful prefix and counts without replaying the old intent" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/recovery-intent").toAbsolutePath),
        "SPEC-").toRealPath()
      Files.createDirectories(root.resolve("capture-root"))
      val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
        GoalPhaseNativeValidation.Check("test", 2, WorkflowSbtValidation.Kind.Test),
        GoalPhaseNativeValidation.Check("test-only", 3, WorkflowSbtValidation.Kind.TestOnly))
      val initial = ThreeWorkflowNormalHostFixture.definition(root)
      val host = initial.copy(goal = initial.goal.copy(candidatePaths = Vector("src"),
        stepValidation = initial.goal.stepValidation.copy(checks = checks)))
      val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component",
        "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
        Vector.empty, NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original-authority", "1")), None, Vector.empty, "SPEC-only")
      val input = V.DelegateTaskInput("sm-workflow", "SPEC-lost-result", "SPEC-owned recovery fixture", NonEmptyVector.one("src"),
        NonEmptyVector.of("compile", "test", "test-only"), NonEmptyVector.one("condition"),
        NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original-source", "1")), false, "code", "off", 10,
        "direct-instruction", Vector.empty, contract, Vector("condition"))
      var session = ""
      var oldRevision = 0L
      var oldHistory = Vector.empty[String]
      val smallCapacity = BoundedWorkExecutionAdmissionFixture.parameters ++ Map("workflow.mechanical-receipt.max-bytes" -> "131072")
      _with_runtime_(root, "Equal", Some(host), smallCapacity) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
        val options = Map("input" -> "SPEC-input.json", "selection-id" -> "SPEC-selection", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        val selected = take(NativeDelegateTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC))
        prepare { setup =>
          val row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val started = read_value[V.DelegateTaskView](take(call("startDelegateTask", List(Property("input", input, None),
          Property("selectionId", "SPEC-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-key", None)))))
        session = started.sessionId
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(org.goldenport.Consequence.success).getOrElse(org.goldenport.Consequence.stateConflict("SPEC Session absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, setup.core)
          observed <- setup.observeC()
          empty = ProcessExecutionCapture(Vector.empty, 0L, false)
          process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), empty, empty, Vector.empty, 1L, "selected-sbt")
          facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)
          receipt = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "", "", Some(facts), Some(process))
          state = payload.state.copy(stage = "ValidationIntent", candidate = Some(StandaloneFixExecution.Candidate(
            observed.head, observed.tree, Vector.empty, "")), receipts = Vector(receipt), pendingCheck = Some("test"),
            automaticCount = 2, ordinaryCount = 1)
          saved <- DelegateTaskNativeRun.saveC(h, store, current, payload.copy(state = state), setup.core)
          _ = { oldRevision = saved.revision.value; oldHistory = saved.history.map(_.causation.value) }
        } yield () }
        val describeInput = V.DelegateTaskValidationRecoveryDescriptionInput(session)
        val described = read_value[V.DelegateTaskValidationRecoveryView](take(call("describeDelegateTaskValidationRecovery",
          List(Property("input", describeInput, None)))))
        val target = take(DelegateTaskValidationRecovery.targetC(described.target))
        target.frontierRevision shouldBe oldRevision
        target.successfulPrefix shouldBe Vector("compile")
        target.captureAvailability shouldBe "not-retained"
        val proof = DelegateTaskValidationRecovery.Proof("SPEC-terminal-event", "SPEC-caller", "output-limit-exceeded",
          Some(143), "failed", 1, "host-attested", "unavailable", "unknown")
        val reference = ContextReference("SPEC-independent-host-attestation", "1")
        val binding = take(DelegateTaskValidationRecovery.bindingC(target, proof, "participant", reference))
        val recovery = V.DelegateTaskValidationRecoveryInput(session, described.target, proof.asJson.noSpaces, "participant",
          V.WorkflowArtifactReference(reference.identity, reference.revision))
        call("recoverDelegateTaskValidation", List(Property("input", recovery, None))).isSuccess shouldBe false
        val live = recovery.copy(proof = proof.copy(callerStatus = "running").asJson.noSpaces)
        call("recoverDelegateTaskValidation", List(Property("input", live, None))).isSuccess shouldBe false
        val foreign = recovery.copy(participant = "SPEC-foreign")
        call("recoverDelegateTaskValidation", List(Property("input", foreign, None))).isSuccess shouldBe false
        val stale = recovery.copy(target = target.copy(frontierRevision = oldRevision + 1).asJson.noSpaces)
        call("recoverDelegateTaskValidation", List(Property("input", stale, None))).isSuccess shouldBe false
        prepare(_.evidenceC(NativeExternalEvidence.Purpose.DelegateTaskValidationRecovery,
          ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision), reference, "participant", binding))
        val capacityRefusal = call("recoverDelegateTaskValidation", List(Property("input", recovery, None)))
        capacityRefusal.isSuccess shouldBe false
        capacityRefusal.toString should include("finite bound")
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        Files.exists(root.resolve("capture-root/validation")) shouldBe false
      }
      _with_runtime_(root, "Equal", Some(host), smallCapacity) { (_, _, _, prepare) =>
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("src"), checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, setup.core)
          instances <- store.bindInstancesC()(using setup.core.executionContext)
          current <- instances.loadC(org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity(session))(using setup.core.executionContext)
            .flatMap(_.map(org.goldenport.Consequence.success).getOrElse(org.goldenport.Consequence.stateConflict("SPEC Session absent")))
          retained <- DelegateTaskNativeRun.loadPayloadC(store, session, oldRevision, setup.core)
          _ <- if (current.revision.value == oldRevision && current.history.map(_.causation.value) == oldHistory &&
            retained.state.stage == "ValidationIntent" && retained.state.pendingCheck.contains("test") && retained.state.receipts.map(_.identity) == Vector("compile") &&
            retained.state.automaticCount == 2 && retained.state.ordinaryCount == 1 && retained.state.input.conditions == Vector("condition") &&
            retained.validationRecovery.isEmpty && retained.validationCaptureReservation.isEmpty) org.goldenport.Consequence.unit
            else org.goldenport.Consequence.stateConflict("SPEC recovery changed its retained Session facts")
        } yield () }
      }
      val correctedCapacity = BoundedWorkExecutionAdmissionFixture.parameters ++ Map("workflow.mechanical-receipt.max-bytes" -> "67108864")
      _with_runtime_(root, "Equal", Some(host), correctedCapacity) { (owner, component, actor, prepare) =>
        def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
        val described = read_value[V.DelegateTaskValidationRecoveryView](take(call("describeDelegateTaskValidationRecovery",
          List(Property("input", V.DelegateTaskValidationRecoveryDescriptionInput(session), None)))))
        val target = take(DelegateTaskValidationRecovery.targetC(described.target))
        target.frontierRevision shouldBe oldRevision
        target.policy.hcursor.get[Int]("mechanicalReceiptBytes").toOption shouldBe Some(67108864)
        val proof = DelegateTaskValidationRecovery.Proof("SPEC-terminal-event", "SPEC-caller", "output-limit-exceeded",
          Some(143), "failed", 1, "host-attested", "unavailable", "unknown")
        val reference = ContextReference("SPEC-fresh-corrected-policy-attestation", "1")
        val binding = take(DelegateTaskValidationRecovery.bindingC(target, proof, "participant", reference))
        prepare(_.evidenceC(NativeExternalEvidence.Purpose.DelegateTaskValidationRecovery,
          ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision), reference, "participant", binding))
        val recovery = V.DelegateTaskValidationRecoveryInput(session, described.target, proof.asJson.noSpaces, "participant",
          V.WorkflowArtifactReference(reference.identity, reference.revision))
        val accepted = read_value[V.DelegateTaskValidationRecoveryView](take(call("recoverDelegateTaskValidation",
          List(Property("input", recovery, None)))))
        accepted.outcome shouldBe "authorized-awaiting-advance"
        call("recoverDelegateTaskValidation", List(Property("input", recovery, None))).isSuccess shouldBe false
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        Files.exists(root.resolve("capture-root/validation")) shouldBe false
      }
    }
  }
  "A compact external receipt" should {
    "reopen full actual CNCF mechanical envelopes with escaped metadata and retained history" in {
      import org.goldenport.cncf.workflow.*
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
      import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
      import StandaloneFixExecution as S
      val definition = take(DelegateTaskRuntimeCatalog.loadC())
      val initial = InstanceRecord(InstanceIdentity("SPEC-session"), definition, initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference("SPEC-correlation"), CausationReference("start"),
        None, None, None, Vector.empty)
      val current = (1 to 32).foldLeft(initial) { (prior, index) =>
        take(prior.appendC(prior.revision, DelegateTaskNativeRun.history(prior, "SPEC-event-\"日本語-" + index)))
      }
      val next = take(current.appendC(current.revision, DelegateTaskNativeRun.history(current, "SPEC-next")))
      val after = take(next.appendC(next.revision, DelegateTaskNativeRun.history(next, "SPEC-result")))
      val input = S.Input("repository", "SPEC-request", "SPEC escaped \"日本語\"", Vector("src"), Vector("test"),
        Vector("condition"), Vector(ContextReference("SPEC-source", "1")), false, scalaLint = "off",
        task = Some(S.Task("direct-instruction", Vector.empty)))
      val check = GoalPhaseNativeValidation.Check("test", 1, WorkflowSbtValidation.Kind.Test)
      val association = WorkflowSbtCapture.Association("SPEC-component", "SPEC-session", next.revision.value,
        "participant", "test", 1, "/SPEC-repository", "main", "a" * 40, "b" * 40)
      val limits = take(WorkflowMechanicalReceiptLimits.createC(67108864))
      val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(definition)))
      Vector((WorkflowSbtValidation.Outcome.Succeeded, "exited", Some(0), false),
        (WorkflowSbtValidation.Outcome.Failed, "exited", Some(1), false),
        (WorkflowSbtValidation.Outcome.Incomplete, "output-limit-exceeded", None, true)).foreach {
        case (outcome, termination, exit, truncated) =>
          val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, outcome, None, None)
          val reference = WorkflowSbtCapture.ExternalReference("sm-workflow-validation-artifacts", "validation/SPEC.json",
            association, 1024L, "a" * 64, 128L, 0L, truncated, false, termination, exit)
          val receipt = RepositorySyncNativeMergeValidation.Receipt("test", "external-unhydrated", None, "", "",
            Some(facts), None, None, Some(reference))
          val control = GoalPhaseRuntimeRepairControl.Context(GoalPhaseRuntimeRepairControl.version, 10)
          val state = S.State(input, "participant", "Wait", 1, Vector(check),
            Some(S.Candidate("a" * 40, "b" * 40, Vector("src/fixture.scala"), "SPEC diff \"日本語\"")),
            Vector(receipt), 2, 1, control,
            findings = Vector.tabulate(24)(i => ("SPEC-" + i + ("雪\"\\" * 512)).take(512)),
            evidenceReferences = Vector(ContextReference("SPEC-" + ("雪\"\\" * 2700), "1")))
          val payload = BoundedWorkRuntime.Payload(state, "/SPEC-repository", "main", "a" * 40)
          val intentState = state.copy(stage = "ValidationIntent", receipts = Vector.empty, findings = Vector.empty,
            evidenceReferences = Vector.empty, pendingCheck = Some("test"))
          val reservation = BoundedWorkRuntime.ValidationCaptureReservation(association, "sm-workflow-validation-artifacts",
            "validation/SPEC.json", WorkflowSbtCapture.maximumArtifactBytes)
          val intentPayload = payload.copy(state = intentState, validationCaptureReservation = Some(reservation))
          val target = DelegateTaskValidationRecovery.Target(DelegateTaskValidationRecovery.schema, "SPEC-component", "SPEC-session",
            "participant", current.revision.value, "/SPEC-repository", "main", "a" * 40, state.candidate.get,
            check, Vector.empty, Json.obj("selectedStdoutCeiling" -> 1024L.asJson, "selectedStderrCeiling" -> 1024L.asJson,
              "processExecution" -> Json.obj("sbtPrograms" -> Json.arr(Json.obj(
                "target" -> "sm-workflow-repository-validation-1".asJson, "operation" -> "test".asJson)))), "absent")
          val host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "repository", "/SPEC-repository", "main", Vector("src"), Vector(check), None, None, None, None, None)
          val bounds = take(DelegateTaskNativeRun.recoveryCapacityBoundsC(host, current, intentPayload, check, association, target))
          val mechanical = WorkflowMechanicalTransition.Receipt(WorkflowMechanicalTransition.Reference("SPEC-checkpoint", "1"),
            org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, DelegateTaskNativeRun.advanceOperation,
            next, after, TypedValue(DelegateTaskNativeRun.stateCodec.typeIdentity, payload))
          val encoded = take(WorkflowMechanicalTransitionJsonV1.encodeC(mechanical, DelegateTaskNativeRun.stateCodec, limits))
          val reopened = take(WorkflowMechanicalTransitionJsonV1.decodeC(encoded, catalog, DelegateTaskNativeRun.stateCodec, limits))
          BigInt(encoded.getBytes(UTF_8).length) should be <= bounds._2
          reopened.current.history.size shouldBe 33
          reopened.next.history.size shouldBe 34
          reopened.payload.value.state.receipts.head.externalCapture shouldBe Some(reference)
          RepositorySyncNativeMergeValidation.productSuccessful(reopened.payload.value.state.receipts.head) shouldBe false
      }
    }
    "roundtrip as an unhydrated reference that cannot satisfy validation" in {
      val association = WorkflowSbtCapture.Association("component", "session", 7L, "participant", "test", 3,
        "/repository", "branch", "base", "tree")
      val reference = WorkflowSbtCapture.ExternalReference("task-artifacts", "capture/result.json", association,
        256L, "a" * 64, 10L, 0L, false, false, "exited", Some(0))
      val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test,
        WorkflowSbtValidation.Outcome.Succeeded, None, None)
      val receipt = RepositorySyncNativeMergeValidation.Receipt("test", "exited", Some(0), "", "",
        Some(facts), None, None, Some(reference))
      val wire = receipt.asJson
      val reopened = wire.as[RepositorySyncNativeMergeValidation.Receipt].toOption.get
      reopened.externalCapture shouldBe Some(reference)
      reopened.processObservation shouldBe None
      RepositorySyncNativeMergeValidation.storedProductOutputValid(reopened) shouldBe false
      RepositorySyncNativeMergeValidation.productSuccessful(reopened) shouldBe false
      val factsWire = wire.hcursor.downField("sbt").focus.get
      def withFacts(value: Json): Json = wire.mapObject(_.add("sbt", value))
      val omitted = factsWire.mapObject(_.remove("selection").remove("reason"))
      val publicRecord = take(new org.goldenport.record.io.RecordDecoder().json(wire.noSpaces))
      val publicJson = io.circe.parser.parse(take(publicRecord.toJsonStringC)).toOption.get
      publicJson.hcursor.downField("sbt").downField("selection").focus shouldBe None
      publicJson.hcursor.downField("sbt").downField("reason").focus shouldBe None
      publicJson.as[RepositorySyncNativeMergeValidation.Receipt].toOption.get.sbt shouldBe Some(facts)
      withFacts(omitted).as[RepositorySyncNativeMergeValidation.Receipt].toOption.get.sbt shouldBe Some(facts)
      Vector("selection", "reason").foreach { key =>
        withFacts(factsWire.mapObject(_.remove(key))).as[RepositorySyncNativeMergeValidation.Receipt].toOption.get.sbt shouldBe Some(facts)
      }
      Vector(
        omitted.mapObject(_.add("unknown", Json.Null)),
        omitted.mapObject(_.add("unknown", Json.True)),
        omitted.mapObject(_.add("reason", "FailedTests".asJson)),
        omitted.mapObject(_.add("selection", Json.obj("unknown" -> Json.True))),
        omitted.mapObject(_.remove("kind")),
        omitted.mapObject(_.add("kind", "TestOnly".asJson))
      ).foreach(value => withFacts(value).as[RepositorySyncNativeMergeValidation.Receipt].isLeft shouldBe true)
      val opened = wire.hcursor.downField("externalCapture").focus.get
      wire.mapObject(_.add("externalCapture", opened.mapObject(_.add("extra", Json.True))))
        .as[RepositorySyncNativeMergeValidation.Receipt].isLeft shouldBe true
    }
    "bind exact failure bytes and reject changed content or association" in {
      val association = WorkflowSbtCapture.Association("component", "session", 7L, "participant", "test", 3,
        "/repository", "branch", "base", "tree")
      val out = ProcessExecutionCapture("failed assertion\n".getBytes(UTF_8).toVector, 17L, false)
      val err = ProcessExecutionCapture(Vector(0x65.toByte), 1L, false)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1), out, err, Vector.empty, 100L, "selected-sbt")
      val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test,
        WorkflowSbtValidation.Outcome.Failed, None, None)
      val encoded = Json.obj("schemaVersion" -> WorkflowSbtCapture.externalSchema.asJson,
        "association" -> association.asJson, "facts" -> facts.asJson,
        "process" -> WorkflowSbtCapture.encode(process)).noSpaces.getBytes(UTF_8)
      val digest = java.security.MessageDigest.getInstance("SHA-256").digest(encoded).map(b => f"${b & 0xff}%02x").mkString
      val ref = WorkflowSbtCapture.ExternalReference("task-artifacts", "capture/result.json", association,
        encoded.length.toLong, digest, out.byteCount, err.byteCount, false, false, "exited", Some(1))
      WorkflowSbtCapture.externalMatches(ref, facts, process) shouldBe true
      WorkflowSbtCapture.externalMatches(ref, facts, process.copy(stdout = out.copy(content = Vector.fill(17)(0x61.toByte)))) shouldBe false
      WorkflowSbtCapture.externalMatches(ref.copy(association = association.copy(intentRevision = 8L)), facts, process) shouldBe false
    }
    "retain structurally valid non-text bytes without admitting them as text or success" in {
      val binary = ProcessExecutionCapture(Vector(0xff.toByte, 0x00.toByte), 2L, false)
      val empty = ProcessExecutionCapture(Vector.empty, 0L, false)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1), binary, empty,
        Vector.empty, 1L, "selected-sbt")
      val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test,
        WorkflowSbtValidation.Outcome.Failed, None, None)
      WorkflowSbtCapture.structurallyValid(process, facts) shouldBe true
      WorkflowSbtCapture.valid(process, facts) shouldBe false
      WorkflowSbtCapture.text(binary).textStatus shouldBe "unavailable"
      WorkflowSbtCapture.decode(WorkflowSbtCapture.encode(process)) shouldBe Right(process)
    }
  }
  "A historical terminal proof" should {
    "reject live, unconfirmed, missing, successful-result and open claims" in {
      val proof = DelegateTaskValidationRecovery.Proof("event:1", "caller:1", "output-limit-exceeded",
        Some(143), "failed", 1, "host-attested", "unavailable", "unknown")
      val canonical = proof.asJson
      DelegateTaskValidationRecovery.proofC(canonical.noSpaces).isSuccess shouldBe true
      Vector(
        canonical.mapObject(_.add("termination", Json.fromString("termination-unconfirmed"))),
        canonical.mapObject(_.add("callerStatus", Json.fromString("running"))),
        canonical.mapObject(_.add("terminalEventReference", Json.fromString(""))),
        canonical.mapObject(_.add("rawCapture", Json.fromString("complete"))),
        canonical.mapObject(_.add("assertionStatus", Json.fromString("passed"))),
        canonical.mapObject(_.add("extra", Json.True))
      ).foreach(wire => DelegateTaskValidationRecovery.proofC(wire.noSpaces).isSuccess shouldBe false)
    }
  }
  "The raw process capacity bound" should {
    "use the exact fixed host ceiling conservatively and refuse ambiguous or malformed declarations" in {
      Given("a selected TestOnly with default 64/8 MiB output policy and a uniquely fixed small host")
      val check = GoalPhaseNativeValidation.Check("test-only", 3, WorkflowSbtValidation.Kind.TestOnly)
      val program = Json.obj("target" -> "sm-workflow-repository-validation-3".asJson,
        "operation" -> "test-only".asJson, "testClasses" -> Vector("example.SelectedSpec").asJson,
        "limits" -> Json.obj("stdoutBytes" -> 786432L.asJson, "stderrBytes" -> 16384L.asJson))
      def _policy_(programs: Vector[Json]) = Json.obj("selectedStdoutCeiling" -> (64L * 1024 * 1024).asJson,
        "selectedStderrCeiling" -> (8L * 1024 * 1024).asJson,
        "processExecution" -> Json.obj("sbtPrograms" -> programs.asJson))
      When("preflight intersects only the selected ceiling with the exact declared fixed upper bound")
      val bounds = take(DelegateTaskNativeRun.selectedCaptureBoundsC(_policy_(Vector(program)), check))
      Then("the valid small host fits the common artifact budget without raising either policy")
      bounds shouldBe ((786432L, 16384L))
      WorkflowSbtCapture.maximumEncodedProcessBytes(bounds._1, bounds._2).get should be < WorkflowArtifactBudget.defaultMaximumBytes.toLong
      val missing = program.mapObject(_.remove("limits"))
      take(DelegateTaskNativeRun.selectedCaptureBoundsC(_policy_(Vector(missing)), check)) shouldBe ((64L * 1024 * 1024, 8L * 1024 * 1024))
      val large = program.mapObject(_.add("limits", Json.obj("stdoutBytes" -> Long.MaxValue.asJson, "stderrBytes" -> Long.MaxValue.asJson)))
      take(DelegateTaskNativeRun.selectedCaptureBoundsC(_policy_(Vector(large)), check)) shouldBe ((64L * 1024 * 1024, 8L * 1024 * 1024))
      WorkflowSbtCapture.maximumEncodedProcessBytes(64L * 1024 * 1024, 8L * 1024 * 1024).get should be > WorkflowArtifactBudget.defaultMaximumBytes.toLong
      Vector(Json.Null, Json.fromLong(0), Json.fromLong(-1), Json.fromString("786432"), Json.fromBigDecimal(BigDecimal("1.5")),
        Json.fromBigInt(BigInt(Long.MaxValue) + 1)).foreach { invalid =>
        val malformed = program.mapObject(_.add("limits", Json.obj("stdoutBytes" -> invalid)))
        DelegateTaskNativeRun.selectedCaptureBoundsC(_policy_(Vector(malformed)), check).isSuccess shouldBe false
      }
      Vector(Vector.empty, Vector(program, program), Vector(program.mapObject(_.add("operation", "test".asJson))),
        Vector(program.mapObject(_.add("target", "sm-workflow-repository-validation-2".asJson))))
        .foreach(programs => DelegateTaskNativeRun.selectedCaptureBoundsC(_policy_(programs), check).isSuccess shouldBe false)
    }
    "cover actual canonical JSON across Base64 boundaries and escaped raw bytes" in {
      for {
        out <- 0 to 12
        err <- 0 to 12
        terminal <- Vector(ProcessExecutionTermination.Exited(Int.MinValue),
          ProcessExecutionTermination.TerminationUnconfirmed,
          ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
          ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr))
      } {
        def capture(n: Int) = ProcessExecutionCapture(Vector.fill(n)(0xff.toByte), n.toLong, false)
        val process = ProcessExecutionResult(terminal, capture(out), capture(err), Vector.empty,
          Long.MaxValue, "a" * 64)
        val size = WorkflowSbtCapture.encode(process).noSpaces.getBytes(UTF_8).length.toLong
        size should be <= WorkflowSbtCapture.maximumEncodedProcessBytes(out.toLong, err.toLong).get
      }
    }

    "refuse negative and overflowed limits without wrapping" in {
      WorkflowSbtCapture.maximumEncodedProcessBytes(-1, 0) shouldBe None
      WorkflowSbtCapture.maximumEncodedProcessBytes(Long.MaxValue, Long.MaxValue) shouldBe None
      val bounded = WorkflowSbtCapture.maximumEncodedProcessBytes(64L * 1024 * 1024, 8L * 1024 * 1024).get
      bounded should be > (96L * 1024 * 1024)
      bounded should be < (128L * 1024 * 1024)
    }
  }
}

/** Explicit qualification of only the two previously failed registrations.
  * The base specification retains its complete ordinary test selection.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskTestOnlyRecoveryFailedCasesSpec extends DelegateTaskValidationRecoveryOperationSpec {
  private val _failed_cases = Vector(
    "The selected TestOnly stopped boundary should retain actual Compile and FixCheck across recorded Incomplete direction and one remaining attempt",
    "The managed lost-result boundary should start one new full Test after an independently attested ended output-limited lost result")

  override protected def runTests(testName: Option[String], args: org.scalatest.Args): org.scalatest.Status = {
    require(_failed_cases.size == 2 && _failed_cases.distinct == _failed_cases && _failed_cases.forall(testNames.contains),
      "Both exact previously failed registrations must exist before qualification")
    val selected = testName.fold(_failed_cases)(name => _failed_cases.filter(_ == name))
    new org.scalatest.CompositeStatus(selected.map(name => super.runTests(Some(name), args)).toSet)
  }
}

/** Explicit qualification of the remaining legacy full-Test recovery case.
  * The ordinary base and two-case qualification retain their selections.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskFullTestRecoveryRemainingSpec extends DelegateTaskValidationRecoveryOperationSpec {
  private val _remaining_case =
    "The managed lost-result boundary should start one new full Test after an independently attested ended output-limited lost result"

  override protected def runTests(testName: Option[String], args: org.scalatest.Args): org.scalatest.Status = {
    require(testNames.contains(_remaining_case), "The exact remaining full-Test registration must exist before qualification")
    if (testName.forall(_ == _remaining_case)) super.runTests(Some(_remaining_case), args)
    else new org.scalatest.CompositeStatus(Set.empty[org.scalatest.Status])
  }
}
