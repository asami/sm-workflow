package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitOperation, GitSelectionId}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.{Record, RecordPresentable}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskAdoptionPreparation
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import BoundedWorkRuntime.*
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import SplitPhaseExplicitPartitionFixture.take

/** Matching-commit/result transfer qualification. Initial accepted semantics and
  * check receipts are specification-owned checkpoint inputs, not fresh SBT or
  * genuine AI/Human evidence. Git commit and collection are actual managed
  * effects. The opt-in variant uses distinct selected installed CLI processes.
  * Neither variant is the authorized live existing-chat D2 delivery.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskReconciledResultSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val _native = DelegateTaskNativeRun
  private val _checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("focused", 2, WorkflowSbtValidation.Kind.TestOnly))
  private def _quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
  private def _contract(role: String) = DelegateTask.Contract("specification-requester", "specification-destination",
    "specification-requester", "org.example.ReconciledFixture", role, "localized",
    Vector("dispatch", "implement", "validate", "commit", "review", "return", "adopt"), Vector("remote-push"),
    Vector(ContextReference("specification-only-direction", "1")), None, Vector.empty,
    "This fixture qualifies result attachment only; no publication, activation or real chat execution is claimed")
  private def _input(role: String) = S.Input("fixture-repository", "reconciled-delivery", "Retain the checked selected value",
    Vector("value.txt"), _checks.map(_.identity), Vector("behavior"), Vector(ContextReference("specification-source", "1")),
    false, scalaLint = "off", task = Some(S.Task("direct-instruction", Vector.empty)), delegation = Some(_contract(role)))
  private def _state(view: V.DelegateTaskView): S.State =
    _decodedState(parser.parse(take(view.state.get.toJsonStringC)).toOption.get)
  private def _decodedState(value: Json): S.State =
    take(GoalPhaseNativeMetadata.normalizePublicJson(value)
      .flatMap(_.as[S.State].left.map(_.message)).fold(Consequence.stateConflict(_), Consequence.success))
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _host(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    original.copy(goal = original.goal.copy(input = original.goal.input.copy(repositoryIdentity = "fixture-repository"),
      candidatePaths = Vector("value.txt"), stepValidation = original.goal.stepValidation.copy(checks = _checks)),
      repository = original.repository.copy(input = original.repository.input.copy(repositoryIdentity = "fixture-repository")))
  }
  private def _bounded(root: Path): Host = Host(_configuration_, _collection_("instances"), _collection_("continuations"),
    "fixture-repository", root.resolve("repository").toString, "main", Vector("value.txt"), _checks,
    Some(take(BoundedWorkExecutionAdmissionFixture.mappingC())), Some(BoundedWorkExecutionAdmissionFixture.origin),
    None, None, None)
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = Vector("value.txt"),
      goalMessages = Vector("Fixture reconciled delegated result"), goalSelections = Vector(Vector("value.txt")))
      .mapObject(fields => fields.add("gitPrograms", Json.fromValues(fields("gitPrograms").get.asArray.get.map { program =>
        val selected = if (program.hcursor.get[String]("selectionId").toOption.contains("goal-step-1"))
          program.mapObject(_.add("selectionId", "delegate-task".asJson)) else program
        selected.mapObject(_.add("gitExecutable", root.resolve("observed-git").toString.asJson))
      })))

  private def _prepare(root: Path): Unit = {
    val repository = root.resolve("repository")
    Files.writeString(repository.resolve("value.txt"), "baseline selected value\n")
    Files.writeString(repository.resolve("unrelated.txt"), "baseline unrelated value\n")
    take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", "value.txt", "unrelated.txt")))
    take(_run_setup_command_(Vector("-C", repository.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
      "commit", "-m", "Fixture baseline for reconciled delivery")))
    Files.writeString(repository.resolve("value.txt"), "checked selected implementation\n")
    Files.writeString(repository.resolve("unrelated.txt"), "unrelated dirty bytes to retain\n")
    val wrapper = "#!/bin/sh\nfor arg in \"$@\"; do\n if [ \"$arg\" = commit ]; then\n" +
      "  printf '%s\\n' commit >> " + _quote(root.resolve("commit-calls").toString) + "\n" +
      "  /usr/bin/git \"$@\"\n  commit_exit=$?\n  if [ \"$commit_exit\" -eq 0 ]; then exit 1; fi\n  exit \"$commit_exit\"\n fi\ndone\nexec /usr/bin/git \"$@\"\n"
    val executable = root.resolve("observed-git")
    Files.writeString(executable, wrapper)
    executable.toFile.setExecutable(true) shouldBe true
  }
  private def _calls(root: Path): Long = {
    val path = root.resolve("commit-calls")
    if (Files.exists(path)) Files.readString(path).linesIterator.size.toLong else 0L
  }
  private def _seedC(root: Path, h: Host, id: String, state: S.State, core: ActionCall.Core): Consequence[Unit] = for {
    definition <- DelegateTaskRuntimeCatalog.loadC()
    storage <- _native.storageC(h, definition, core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    initial <- InstanceRecord(InstanceIdentity(id), definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference("specification-checkpoint"), CausationReference("specification-checkpoint"), None, None, None, Vector.empty).validateC
    _ <- instances.createC(initial)(using core.executionContext)
    active <- instances.appendC(initial.identity, initial.revision, _native.history(initial, "specification-retained-checkpoint"))(using core.executionContext)
    observed <- G.observeC(core)
    _ <- _native.saveC(h, storage, active, Payload(state, h.root, "main", state.candidate.fold(observed.head)(_.head),
      unrelated = Some(observed.dirty.filterNot(_.path == "value.txt"))), core)
  } yield ()

  private def _qualify(installed: Option[(Path, Path)], unknown: Boolean): Unit = {
    val scenario = NativeWorkflowCliFixture.fresh("p68-reconciled-" + unknown, bootstrapSelectedInput = false)
    val root = scenario.root
    _prepare(root)
    val h = _bounded(root)
    val host = _host(root)
    val parameters = BoundedWorkExecutionAdmissionFixture.parameters
    val destination = DelegateTask.sessionIdentity(_input("destination"))
    val requester = DelegateTask.sessionIdentity(_input("requester"))
    var original = Option.empty[S.State]
    def withRuntime(body: (org.goldenport.cncf.subsystem.Subsystem, org.goldenport.cncf.component.Component,
      org.goldenport.cncf.context.ExecutionContext, (Setup => Consequence[Unit]) => Unit) => Unit): Unit =
      _with_runtime_(root, "ReconciledDelegate", Some(host), parameters, componentInstance = "default")(body)
    withRuntime { (_, _, _, project) =>
      project(call => for {
        before <- G.observeC(call.core)
        _ <- E.stageGoalCandidateC(call.core, 1).flatMap(_.valueC)
        tree <- G.indexTreeC(call.core)
        ready <- S.startC(_input("destination"), "participant", _checks)
        receipts = _checks.map(check => RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(0),
          "SPECIFICATION-OWNED retained accepted check; no fresh SBT result claimed", "", Some(WorkflowSbtValidation.Facts(
            check.kind, WorkflowSbtValidation.Outcome.Succeeded, Option.when(check.kind == WorkflowSbtValidation.Kind.TestOnly)(
              WorkflowSbtValidation.Selection(Vector("example.SpecificationBehaviorSpec"), 1, 1L)), None))))
        retained = ready.copy(stage = if (unknown) "CommitIntent" else "CommitReady", automaticCount = 2, ordinaryCount = 1,
          candidate = Some(S.Candidate(before.head, tree, Vector("value.txt"), "Specification-owned accepted candidate")),
          receipts = receipts, semanticReference = Some(ContextReference("specification-accepted-implementation", "1")),
          conditionReport = Vector(S.Condition("behavior", true, "Specification-owned accepted semantic condition")))
        _ <- _seedC(root, h, destination, retained, call.core)
        request <- S.startC(_input("requester"), "participant", _checks)
        _ <- _seedC(root, h, requester, request.copy(stage = "DispatchReady"), call.core)
        // Model the exact historical missing-receipt boundary using a managed
        // fixture intent followed by an actual effect. No raw workflow row edit.
        _ <- if (!unknown) Consequence.unit else for {
          selection <- GitSelectionId.parseC("delegate-task")
          effect <- E.executeGitC(call.core, GitOperation.CommitSelected(selection))
          _ = WorkflowSbtCapture.exitCode(effect.processResult) shouldBe Some(1)
        } yield ()
        _ = original = Some(retained)
      } yield ())
    }
    def registerInstalled(): Unit = {
      val path = root.resolve(".textus/assembly.yaml")
      val text = Files.readString(path)
      require(text.contains("components: []"), "Select only this managed fixture host")
      Files.writeString(path, text.replace("components: []",
        "components:\n  - namespace: org.simplemodeling.textus\n    id: SmWorkflow\n    version: 0.1.0-SNAPSHOT"))
    }
    if (installed.nonEmpty) registerInstalled()
    def value[A: org.goldenport.convert.ValueReader](service: String, operation: String, properties: List[Property],
      arguments: Vector[String]): A = installed match {
      case Some((executable, installations)) => scenario.callInstalled(executable, service, operation, arguments,
        Some(installations), Some(root.resolve(".textus/assembly.yaml"))).business[A]
      case None =>
        var result = Option.empty[A]
        withRuntime { (owner, component, actor, _) =>
          result = Some(read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = service, operation = operation, properties = properties), actor))))
        }
        result.get
    }
    def advance(id: String): V.DelegateTaskView = value("DelegateTaskService", "advanceDelegateTask",
      List(Property("sessionId", id, None)), Vector("--sessionId", id))
    def observation(event: String, result: Option[Record] = None, artifact: Option[Record] = None) = V.DelegateTaskObservationInput(
      requester, event, V.WorkflowArtifactReference("specification-transport-" + event, "1"), Some(destination), result, artifact,
      "SPECIFICATION-OWNED observation; no real chat transport, consent, publication or activation claimed")
    def observe(input: V.DelegateTaskObservationInput): V.DelegateTaskView = value("DelegateTaskDeliveryService",
      "observeDelegateTaskDelivery", List(Property("input", input, None)), Vector("--input", _json(input)))
    val sent = observe(observation("dispatch-observed"))
    _state(sent).stage shouldBe "AwaitResult"
    var failed = Option.empty[S.State]
    if (!unknown) {
      val first = advance(destination)
      first.kind shouldBe "Incomplete"
      failed = Some(_state(first))
      failed.get.stage shouldBe "CommitFailed"
      failed.get.commitExecution.flatMap(_.exitCode) shouldBe Some(1)
      failed.get.commit shouldBe None
    }
    _calls(root) shouldBe 1L
    When("the actual native owner observes the already-created matching commit without repeating it")
    val returned = advance(destination)
    returned.kind shouldBe "Return"
    val producer = _state(returned)
    producer.stage shouldBe "ReturnReady"
    producer.commitReconciliation.map(_.sourceStage) shouldBe Some(if (unknown) "CommitIntent" else "CommitFailed")
    producer.commitExecution shouldBe failed.flatMap(_.commitExecution)
    producer.commitReconciliation.flatMap(_.execution) shouldBe producer.commitExecution
    producer.candidate shouldBe original.get.candidate
    producer.receipts shouldBe original.get.receipts
    producer.conditionReport shouldBe original.get.conditionReport
    producer.automaticCount shouldBe 2
    producer.ordinaryCount shouldBe 1
    val commit = producer.commit.get
    commit.parent shouldBe original.get.candidate.get.head
    commit.tree shouldBe original.get.candidate.get.tree
    advance(destination).state shouldBe returned.state
    _calls(root) shouldBe 1L
    Then("the requester can collect and separately adopt that exact result while preserving the original failed or UNKNOWN evidence")
    val receive = observation("result-received", returned.state)
    val received = observe(receive)
    _state(received).stage shouldBe "ResultReceived"
    observe(receive).state shouldBe received.state
    advance(requester).state shouldBe received.state
    val selected = Record.data("sourceCommit" -> commit.identity, "coordinates" -> Vector.empty[String],
      "selectionReference" -> Record.data("identity" -> "specification-artifact-selection", "revision" -> "1"),
      "consumerCheckReferences" -> Vector.empty[Record])
    val adoption = observation("adopt", artifact = Some(selected))
    val context = value[V.DelegateTaskAdoptionContext]("DelegateTaskDeliveryService", "describeDelegateTaskAdoption",
      List(Property("sessionId", requester, None)), Vector("--sessionId", requester))
    withRuntime { (_, _, _, project) =>
      project(call => for {
        prepared <- NativeDelegateTaskAdoptionPreparation.prepareC(context, adoption, adoption.sourceReference,
          V.WorkflowExecutionEvidence(Vector(adoption.sourceReference), Some("participant"), None, None),
          "normal-development-host", call.executionContext.clock.instant().minusSeconds(1))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
    }
    if (installed.nonEmpty) registerInstalled()
    val adopted = observe(adoption)
    adopted.kind shouldBe "Terminal"
    _state(adopted).stage shouldBe "Adopted"
    _state(adopted).delivery.get.returned.map(_decodedState) shouldBe Some(_state(returned))
    observe(adoption).state shouldBe adopted.state
    advance(requester).state shouldBe adopted.state
    advance(destination).state shouldBe returned.state
    _calls(root) shouldBe 1L
    Files.readString(root.resolve("repository/unrelated.txt")) shouldBe "unrelated dirty bytes to retain\n"
    installed.foreach { (executable, _) =>
      scenario.invocations should not be empty
      scenario.invocations.foreach(_.argv.head shouldBe executable.toString)
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
    }
    // These contrasts exercise result data, not trusted remote storage. Original
    // role/owner/scope/check/review/publication gates remain in returnedC.
    val requesterInput = _input("requester")
    take(DelegateTask.returnedC(requesterInput, producer.asJson)) shouldBe producer
    val proof = producer.commitReconciliation.get
    Vector(producer.copy(commitReconciliation = None),
      producer.copy(commitReconciliation = Some(proof.copy(commit = commit.copy(parent = "f" * 40)))),
      producer.copy(commitReconciliation = Some(proof.copy(candidateTree = "f" * 40))),
      producer.copy(commitReconciliation = Some(proof.copy(candidatePaths = Vector("unrelated.txt")))),
      producer.copy(commitReconciliation = Some(proof.copy(sourceStage = "CommitReady"))),
      producer.copy(commitReconciliation = Some(proof.copy(execution = Some(S.CommitExecution("Succeeded", Vector("value.txt"), "exited", Some(0), "", ""))))),
      producer.copy(commit = Some(commit.copy(tree = "f" * 40)))).foreach { crossed =>
      DelegateTask.returnedC(requesterInput, crossed.asJson).isSuccess shouldBe false
    }
    // Old ordinary successful results without the new observation are retained.
    val legacy = producer.copy(commitReconciliation = None,
      commitExecution = Some(S.CommitExecution("Succeeded", Vector("value.txt"), "exited", Some(0), "old actual-shaped success", "")))
    take(DelegateTask.returnedC(requesterInput, legacy.asJson)) shouldBe legacy
    legacy.asJson.hcursor.downField("commitReconciliation").focus shouldBe None
    legacy.asJson.as[S.State].toOption shouldBe Some(legacy)
  }

  "Reconciled delegated commit results" should {
    "retain failed and missing execution observations through managed collection requester adoption and re-entry" in {
      Given("two fresh fixture-owned native checkpoints, with accepted semantics/checks explicitly marked as specification data")
      Vector(false, true).foreach(unknown => _qualify(None, unknown))
    }
    "qualify the affected result path in the explicitly selected installed image" in {
      val command = sys.props.get("smworkflow.spec.installedCommand").orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      val root = sys.props.get("smworkflow.spec.phase68Installations").orElse(sys.env.get("SM_WORKFLOW_SPEC_PHASE68_INSTALLATIONS"))
      if (command.isEmpty || root.isEmpty) cancel("Installed affected-path qualification unavailable: explicitly select both installed command and Phase68 installation root")
      val executable = Path.of(command.get)
      val installations = Path.of(root.get)
      require(executable.isAbsolute && Files.isRegularFile(executable) && Files.isExecutable(executable), "Select the actual installed executable")
      require(installations.isAbsolute && Files.isRegularFile(installations.resolve("selected")), "Select the actual registered installation root")
      val selected = Files.readString(installations.resolve("selected"))
      val selection = parser.parse(selected).toOption.get
      selection.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime-selection.v1")
      val identity = selection.hcursor.get[String]("installation_id").toOption.get
      val directory = installations.resolve(identity).normalize
      directory.getParent shouldBe installations
      val manifestBytes = Files.readAllBytes(directory.resolve("runtime-installation.json"))
      val manifest = parser.parse(new String(manifestBytes, java.nio.charset.StandardCharsets.UTF_8)).toOption.get
      manifest.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime.v1")
      val component = manifest.hcursor.downField("component")
      component.get[String]("identity") shouldBe Right("org.simplemodeling.textus.SmWorkflow")
      val archive = directory.resolve(component.get[String]("path").toOption.get).normalize
      require(archive.startsWith(directory.resolve("components")) && Files.isRegularFile(archive) && !Files.isSymbolicLink(archive),
        "Select the immutable installed component archive")
      def digest(bytes: Array[Byte]): String = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
      val archiveDigest = digest(Files.readAllBytes(archive))
      info("Installed affected-path provenance: installation=" + identity + " command=" + executable +
        " manifestSha256=" + digest(manifestBytes) + " component=" + archive + " componentSha256=" + archiveDigest)
      Given("an explicitly selected immutable installed image; no source-classpath or global-image fallback")
      Vector(false, true).foreach(unknown => _qualify(Some(executable -> installations), unknown))
      Files.readString(installations.resolve("selected")) shouldBe selected
      Files.readAllBytes(directory.resolve("runtime-installation.json")).toVector shouldBe manifestBytes.toVector
      digest(Files.readAllBytes(archive)) shouldBe archiveDigest
    }
  }
}
