package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import cats.data.NonEmptyVector
import io.circe.Json
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

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskValidationRecoveryOperationSpec extends AnyWordSpec with Matchers with ThreeWorkflowManagedRepositoryFixture {
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = super.process_plan(root, scenario, goal)
    val baseline = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2), Some(git),
      operation = SbtOperation.Test, stdoutBytes = 2097152, stderrBytes = 131072)
    val plan = if (!Files.exists(root.resolve("SPEC-new-capture-policy"))) baseline else {
      val programs = baseline.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map(_.mapObject(program =>
        program.add("limits", program("limits").get.mapObject(_.add("stdoutBytes", Json.fromLong(8L * 1024 * 1024))))))
      baseline.mapObject(_.add("sbtPrograms", programs.asJson))
    }
    val workspace = Json.obj("identity" -> "sm-workflow-validation-artifacts".asJson,
      "root" -> root.resolve("capture-root").toString.asJson)
    val grant = Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "workspaceId" -> "sm-workflow-validation-artifacts".asJson, "access" -> "manage".asJson)
    plan.mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(_ :+ workspace))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ grant)))
  }
  "The managed external capture owner" should {
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
  private[admission] def qualifyInstalledAvailableRecovery(executable: Path, component: Path): Unit =
    qualifyAvailableRecovery(Some(executable -> component))

  private def qualifyAvailableRecovery(installed: Option[(Path, Path)]): Unit = {
      import org.goldenport.Consequence
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
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
        NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original-source", "1")), false, "code", "off", 10,
        "direct-instruction", Vector.empty, contract, Vector("first"))
      var session = ""
      var intentRevision = 0L
      var association = Option.empty[WorkflowSbtCapture.Association]
      var actual = Option.empty[SbtExecutionResult]
      var raw = Option.empty[WorkflowSbtCapture.ExternalReference]
      var legacyFailure = ""
      var oldHistory = Vector.empty[String]
      val artifactPath = "validation/SPEC-real-result.json"
      val artifactLimits = org.goldenport.cncf.resource.ResourceReadLimits(4 * 1024 * 1024)
      val currentParameters = BoundedWorkExecutionAdmissionFixture.parameters ++ Map("workflow.mechanical-receipt.max-bytes" -> "16777216")
      _with_runtime_(root, "Equal", Some(host), currentParameters, componentInstance = "default") { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
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
          prefixProcess = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), empty, empty, Vector.empty, 1L, "selected-sbt")
          prefixFacts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)
          prefix = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "", "", Some(prefixFacts), Some(prefixProcess))
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
      def installedCall[A: org.goldenport.convert.ValueReader](operation: String, value: org.goldenport.record.RecordPresentable): A = {
        val executable = installed.map(_._1).getOrElse(fail("No installed executable was selected"))
        val selectedComponent = installed.map(_._2).getOrElse(fail("No candidate Component CAR was selected"))
        val config = root.resolve(".textus/config.json")
        val configured = io.circe.parser.parse(Files.readString(config)).toOption.get
        Files.writeString(config, configured.mapObject(_.add("textus.component.file", selectedComponent.toString.asJson)).noSpaces)
        val logs = Files.createDirectories(root.resolve("installed-recovery-logs"))
        sequence += 1
        val stdout = logs.resolve(sequence + ".stdout.json")
        val stderr = logs.resolve(sequence + ".stderr")
        val argv = Vector(executable.toString, org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name +
          ".DelegateTaskService." + operation, "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"),
          "--input", take(value.toRecord().toJsonStringC), "--json")
        val process = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
        try {
          process.getOutputStream.close()
          withClue("Installed candidate operation timed out; stderr=" + stderr) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
          withClue("Installed candidate operation failed; stderr=" + Files.readString(stderr)) { process.exitValue() shouldBe 0 }
          val record = take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(stdout)))
          take(summon[org.goldenport.convert.ValueReader[A]].readC(record))
        } finally {
          if (process.isAlive) { process.destroy(); if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly() }
          process.getInputStream.close(); process.getErrorStream.close()
        }
      }
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
          state = payload.state.copy(stage = "ValidationIntent", candidate = Some(candidate), receipts = Vector(prefix),
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
        // A distinct public Advance saves the new intent before the one new managed Test.
        take(call("advanceDelegateTask", List(Property("sessionId", session, None))))
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
            check, Vector.empty, Json.obj("selectedStdoutCeiling" -> 1024L.asJson, "selectedStderrCeiling" -> 1024L.asJson), "absent")
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
