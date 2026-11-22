package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import BoundedWorkRuntime.given
import DelegateTaskRepairContinuation as R

/** The facts here belong to this specification, not any real delegation.
  * @since Nov. 12, 2026
  * @version Nov. 20, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
abstract class DelegateTaskRepairContinuationCodecSpec extends AnyWordSpec with org.scalatest.matchers.should.Matchers with GivenWhenThen {
  private val _contract = DelegateTask.Contract("requester", "destination", "requester", "component", "destination",
    "localized", Vector("dispatch", "implement", "validate", "commit", "return", "adopt", "review"), Vector.empty, Vector(ContextReference("authorization", "1")), None, Vector.empty, "fixture")
  private val _goal = GoalTaskRepairContinuationFixture.payload
  private val _payload = _goal.copy(state = _goal.state.copy(input = _goal.state.input.copy(delegation = Some(_contract))))
  private val _target = R.Target("component", "session", _payload.state.participant, 8L, Json.obj("fixture" -> true.asJson), _payload)

  "DelegateTask repair continuation" should {
    "retain exact eligible targets while refusing other ownership and unknown failures" in {
      Given("an exact specification-owned legacy failed trend target")
      When("its legacy wire is decoded and qualification is queried")
      val decoded = R.decodeTarget(_target.asJson)
      Then("only its destination Task and eligible failed facts are retained")
      R.eligible(_payload) shouldBe true
      decoded shouldBe Right(_target)
      GoalTaskRepairContinuation.decodeTarget(_target.asJson).isLeft shouldBe true
      R.decodeTarget(GoalTaskRepairContinuationFixture.target.asJson).isLeft shouldBe true
      R.eligible(_goal) shouldBe false
      R.eligible(_payload.copy(state = _payload.state.copy(input = _payload.state.input.copy(
        delegation = Some(_contract.copy(role = "requester")))))) shouldBe false
      R.eligible(_payload.copy(state = _payload.state.copy(automaticCount = 10))) shouldBe false
      R.eligible(_payload.copy(state = _payload.state.copy(pendingCheck = Some("compile")))) shouldBe false
      R.eligible(_payload.copy(state = _payload.state.copy(receipts = _payload.state.receipts.map(_.copy(termination = "unknown"))))) shouldBe false
    }
    "retain counts and history in a separately encoded Repair checkpoint" in {
      Given("the original eligible failed trend target and independent direction")
      val direction = R.Direction(_target.asJson, "ContinueRepair", "Specification direction", _target.participant,
        ContextReference("spec-human", "1"))
      val next = _payload.copy(state = _payload.state.copy(stage = "Repair"), delegateRepairContinuation = Some(direction))
      When("the successor is encoded and reopened")
      val restored = next.asJson.as[BoundedWorkRuntime.Payload].toOption.get
      Then("native accounting and all diagnostic facts remain exact")
      restored shouldBe next
      restored.state.automaticCount shouldBe _payload.state.automaticCount
      restored.state.ordinaryCount shouldBe _payload.state.ordinaryCount
      restored.state.receipts shouldBe _payload.state.receipts
      restored.state.repairControl shouldBe _payload.state.repairControl
      restored.state.generation shouldBe _payload.state.generation
      val later = _target.copy(frontierRevision = 10L, business = _payload.copy(delegateRepairContinuation = Some(direction)))
      R.decodeTarget(later.asJson) shouldBe Right(later)
    }
    "retain a compact successful TestOnly prefix before an inline ended full Test in preparation" in {
      Given("Compile then external successful TestOnly metadata and an inline ended Incomplete full Test")
      val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
        GoalPhaseNativeValidation.Check("test-only", 2, WorkflowSbtValidation.Kind.TestOnly),
        GoalPhaseNativeValidation.Check("test", 3, WorkflowSbtValidation.Kind.Test))
      val compile = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "[success] compiled", "",
        Some(WorkflowSbtValidation.Facts(checks.head.kind, WorkflowSbtValidation.Outcome.Succeeded, None, None)))
      val association = WorkflowSbtCapture.Association(_target.componentInstance, _target.sessionId, 6L,
        _payload.state.participant, "test-only", 2, _payload.root, _payload.branch,
        _payload.state.candidate.get.head, _payload.state.candidate.get.tree)
      val reference = WorkflowSbtCapture.ExternalReference("sm-workflow-validation-artifacts", "validation/SPEC-prefix.json",
        association, 1024L, "b" * 64, 100L, 0L, false, false, "exited", Some(0))
      val selected = RepositorySyncNativeMergeValidation.Receipt("test-only", "external-unhydrated", None, "", "",
        Some(WorkflowSbtValidation.Facts(checks(1).kind, WorkflowSbtValidation.Outcome.Succeeded,
          Some(WorkflowSbtValidation.Selection(Vector(classOf[DelegateTaskRepairContinuationOperationSpec].getName), 1, 1L)), None)),
        externalCapture = Some(reference))
      val ended = TerminatedValidationFixture.receipt(checks.last)
      val state = _payload.state.copy(stage = "Wait", selected = checks, receipts = Vector(compile, selected, ended),
        input = _payload.state.input.copy(checks = checks.map(_.identity)))
      val target = _target.copy(business = _payload.copy(state = state), source = Some(R.Source.RecordedIncomplete))
      When("the public target and exact Human preparation binding are decoded")
      val decoded = R.decodeTarget(target.asJson).toOption.get
      val binding = R.bindingC(decoded.componentInstance, decoded, "RepairBeforeRetry", "SPEC correct the full Test source",
        decoded.participant, ContextReference("SPEC-mixed-capture-human", "1")).toOption.get
      import org.simplemodeling.textus.smworkflow.value as V
      val human = V.WorkflowArtifactReference("SPEC-mixed-capture-human", "1")
      val description = V.GoalTaskRepairContinuationDescriptionResult(decoded.sessionId, decoded.componentInstance,
        "decision-required", "SPEC mixed capture storage", decoded.asJson.noSpaces, R.choicesFor(decoded), R.resumeOperation)
      val answer = V.GoalTaskRepairContinuationInput(decoded.sessionId, decoded.asJson.noSpaces, "RepairBeforeRetry",
        "SPEC correct the full Test source", decoded.participant, human)
      val prepared = org.simplemodeling.textus.smworkflow.preparation.NativeReviewPreparation.delegateTaskRepairContinuationC(
        description, answer, human, V.WorkflowExecutionEvidence(Vector(human), Some(decoded.participant), None, None),
        decoded.participant, "SPEC-independent-host", java.time.Instant.now())
      Then("the target round trips with its compact prefix intact and evidence selection remains available")
      decoded shouldBe target
      decoded.asJson shouldBe target.asJson
      decoded.business.state.receipts(1).processObservation shouldBe None
      decoded.business.state.receipts.last.processObservation should not be empty
      R.selectBinding(binding) shouldBe Right(binding)
      prepared.isSuccess shouldBe true
      R.eligible(decoded.business) shouldBe false
    }
    "distinguish recorded Incomplete choices and compact capture metadata from executable process facts" in {
      Given("a specification-owned successful Compile prefix and ended TestOnly output-limit observation")
      val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
        GoalPhaseNativeValidation.Check("test-only", 2, WorkflowSbtValidation.Kind.TestOnly))
      val prefix = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "[success] compiled", "",
        Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)))
      val receipt = TerminatedValidationFixture.receipt(checks.last)
      val state = _payload.state.copy(stage = "Wait", input = _payload.state.input.copy(checks = checks.map(_.identity)),
        selected = checks, receipts = Vector(prefix, receipt))
      val business = _payload.copy(state = state)
      val stopped = _target.copy(business = business, source = Some(R.Source.RecordedIncomplete))
      val association = WorkflowSbtCapture.Association(_target.componentInstance, _target.sessionId, 7L,
        state.participant, checks.last.identity, checks.last.ordinal, _payload.root, _payload.branch,
        state.candidate.get.head, state.candidate.get.tree)
      val reference = WorkflowSbtCapture.ExternalReference("sm-workflow-validation-artifacts", "validation/SPEC.json", association,
        1024L, "a" * 64, receipt.processObservation.get.stdout.byteCount, 0L, true, false, "output-limit-exceeded", None)
      val compact = stopped.copy(business = business.copy(state = state.copy(receipts = Vector(prefix,
        receipt.copy(externalCapture = Some(reference))))))
      val timeoutreceipt = receipt.copy(processObservation = None,
        externalCapture = Some(reference.copy(termination = "timed-out")))
      val timeout = compact.copy(business = business.copy(state = state.copy(receipts = Vector(prefix, timeoutreceipt))))
      When("the new target and compact external metadata are decoded")
      val decoded = R.decodeTarget(compact.asJson).toOption.get
      val timeoutdecoded = R.decodeTarget(timeout.asJson).toOption.get
      Then("metadata remains readable without pretending to have reopened the actual process")
      R.eligible(business) shouldBe true
      R.decodeTarget(stopped.asJson) shouldBe Right(stopped)
      R.choicesFor(stopped) shouldBe Vector("RepairBeforeRetry", "Defer")
      R.choicesFor(_target) shouldBe R.choices
      _target.asJson.hcursor.downField("source").focus shouldBe None
      decoded.business.state.receipts.last.processObservation shouldBe None
      R.eligible(decoded.business) shouldBe false
      timeoutdecoded.business.state.receipts.last.externalCapture.get.stdoutTruncated shouldBe true
      timeoutdecoded.business.state.receipts.last.externalCapture.get.termination shouldBe "timed-out"
      timeoutdecoded.business.state.receipts.last.processObservation shouldBe None
      R.eligible(timeoutdecoded.business) shouldBe false
      R.bindingC(stopped.componentInstance, stopped, "ContinueRepair", "SPEC", stopped.participant,
        ContextReference("SPEC-human", "1")).isSuccess shouldBe false
      R.bindingC(_target.componentInstance, _target, "RepairBeforeRetry", "SPEC", _target.participant,
        ContextReference("SPEC-human", "1")).isSuccess shouldBe false
      val binding = R.bindingC(stopped.componentInstance, stopped, "RepairBeforeRetry", "SPEC", stopped.participant,
        ContextReference("SPEC-human", "1")).toOption.get
      R.selectBinding(binding) shouldBe Right(binding)
      R.decodeTarget(stopped.copy(source = None).asJson).isLeft shouldBe true
      R.decodeTarget(compact.copy(sessionId = "foreign").asJson).isLeft shouldBe true
      R.eligible(business.copy(state = state.copy(receipts = Vector(prefix,
        TerminatedValidationFixture.receipt(checks.last,
          org.goldenport.cncf.processexecution.ProcessExecutionTermination.TerminationUnconfirmed))))) shouldBe false
      R.eligible(business.copy(state = state.copy(automaticCount = state.repairControl.effectiveLimit))) shouldBe false
      R.eligible(business.copy(state = state.copy(input = state.input.copy(kind = "validation")))) shouldBe false
      R.eligible(business.copy(state = state.copy(pendingCheck = Some("test-only")))) shouldBe false
      R.eligible(business.copy(state = state.copy(commit = Some(StandaloneFixExecution.Commit("c" * 40,
        state.candidate.get.head, state.candidate.get.tree))))) shouldBe false
      R.eligible(business.copy(supersededWork = Some("pending-work"))) shouldBe false
      val direction = R.Direction(stopped.asJson, "RepairBeforeRetry", "SPEC retained diagnostics", stopped.participant,
        ContextReference("SPEC-human", "1"))
      val later = stopped.copy(frontierRevision = 10L, business = business.copy(delegateRepairContinuation = Some(direction)))
      R.decodeTarget(later.asJson) shouldBe Right(later)
      R.decodeTarget(later.copy(business = later.business.copy(delegateRepairContinuation = Some(direction.copy(choice = "ContinueRepair")))).asJson).isLeft shouldBe true
    }
  }
}

/** Public operations over one specification-owned managed destination Session.
  * @since Nov. 12, 2026
  * @version Nov. 23, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskRepairContinuationOperationSpec extends DelegateTaskRepairContinuationCodecSpec
    with ThreeWorkflowManagedRepositoryFixture {
  import cats.data.NonEmptyVector
  import java.nio.file.{Files, Path}
  import org.goldenport.Consequence
  import org.goldenport.cncf.processexecution.GitOperationKind
  import org.goldenport.cncf.workflow.*
  import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
  import org.goldenport.protocol.{Property, Request}
  import org.goldenport.record.Record
  import org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskStartInput
  import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
  import org.simplemodeling.textus.smworkflow.value as V
  import StandaloneFixExecution as S
  import S.given
  private def _take[A](value: Consequence[A]): A = value.toOption.get
  private def _stopped_fixture(initial: S.State, candidate: S.Candidate): S.State = {
    val required = initial.input.submissionConditions.getOrElse(initial.input.conditions).toSet
    val completed = S.Result("completed", "Specification repair source ready", Vector.empty,
      initial.input.conditions.map(identity => S.Condition(identity, required(identity), "Specification source readiness")), Vector.empty)
    val implemented = _take(S.completedC(S.Work("fixture", "1", "Implement", initial), completed,
      ContextReference("fixture-implementation", "1")))
      .copy(automaticCount = 2, ordinaryCount = 2, generation = 3)
    (1 to 4).foldLeft(implemented) { (state, ordinal) =>
      val captured = _take(S.candidateC(state, candidate.copy(diff = "fixture-generation-" + state.generation)))
      val check = captured.selected.head
      val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1),
        "[error] retained specification check failure", "", Some(WorkflowSbtValidation.Facts(check.kind,
          WorkflowSbtValidation.Outcome.Failed, None, None)))
      val observed = _take(S.observedC(_take(S.intentC(captured)), receipt))
      if (ordinal == 4) observed else _take(S.completedC(S.Work("fixture", ordinal.toString, "Repair", observed),
        completed, ContextReference("fixture-repair-" + ordinal, "1")))
    }
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++ Vector(
        GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff), goalPaths = Vector("docs/phase.md"))
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    val file = root.resolve("repository/docs/phase.md")
    Files.createDirectories(file.getParent)
    Files.writeString(file, "Specification-owned original source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "docs/phase.md")), 0)

  private[admission] def qualify(installed: Option[(Path, Path)], recordedIncomplete: Boolean = false): Unit = {
    import scala.jdk.CollectionConverters.*
    import java.util.concurrent.TimeUnit
    installed.foreach { case (executable, archive) =>
      require(executable.isAbsolute && Files.isExecutable(executable), "Installed command must exist")
      require(archive.isAbsolute && Files.isRegularFile(archive), "Candidate CAR must exist")
    }
    val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-repair-managed").toAbsolutePath), "SPEC-").toRealPath()
    val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component", "destination",
      "documentation", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector.empty,
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-authority", "1")), None, Vector.empty, "SPEC-owned direction", None, None, None)
    val input = V.DelegateTaskInput("sm-workflow", "SPEC-delegate-repair", "SPEC-managed continuation",
      NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original", "1")), false, "documentation", "off", 10,
      "direct-instruction", Vector.empty, contract, Vector.empty, None)
    var session = ""
    var description = Option.empty[V.GoalTaskRepairContinuationDescriptionResult]
    var retained = Option.empty[BoundedWorkRuntime.Payload]
    var savedhistory = Vector.empty[HistoryEntry]
    val parameters = BoundedWorkExecutionAdmissionFixture.parameters ++ Map(
      "textus.resource.url.file.roots" -> root.toString, "textus.resource.read.max-bytes" -> "16777216")
    def _installed_record_(operation: String, argument: Vector[String],
      service: String = "DelegateTaskService"): Record = {
      val (_, archive) = installed.get
      val logs = Files.createTempDirectory(root, "SPEC-installed-")
      val stdout = logs.resolve("stdout.json")
      val stderr = logs.resolve("stderr")
      val argv = NativeWorkflowCliFixture.candidateCommand(archive) ++ Vector(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name +
        "." + service + "." + operation, "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"),
        "--textus.test.descriptor=" + root.resolve(".textus/test.yaml")) ++ argument ++ Vector("--json")
      val process = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        process.getOutputStream.close()
        withClue("installed operation timeout: " + stderr) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
        withClue("installed operation error: " + Files.readString(stderr)) { process.exitValue() shouldBe 0 }
        _take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(stdout)))
      } finally {
        if (process.isAlive) { process.destroy(); if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly() }
        process.getInputStream.close(); process.getErrorStream.close()
      }
    }
    def _installed_call_[A: org.goldenport.convert.ValueReader](operation: String, argument: Vector[String]): A =
      _take(summon[org.goldenport.convert.ValueReader[A]].readC(_installed_record_(operation, argument)))
    def _encoded_(value: org.goldenport.record.RecordPresentable): String = _take(value.toRecord().toJsonStringC)
    Given("one exact specification-owned active destination Session with retained native validation facts")
    _with_runtime_(root, "TaskTrend", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def _call_(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val selected = _take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "SPEC-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      prepare { setup => for {
        _ <- setup.setupC(_setup_commands_(root, "TaskTrend").size)
        row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        _ <- setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
      } yield () }
      val started = read_value[V.DelegateTaskView](_take(_call_("startDelegateTask", List(Property("input", input, None),
        Property("selectionId", "SPEC-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-start", None)))))
      session = started.sessionId
      _call_("describeDelegateTaskRepairContinuation", List(Property("input",
        V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Start is absent")))
        original <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
        observed <- RepositorySyncManagedGitObservation.observeC(setup.core)
        tree <- RepositorySyncManagedGitObservation.indexTreeC(setup.core)
        candidate = S.Candidate(observed.head, tree, Vector.empty, "")
        stopped = if (recordedIncomplete) original.state.copy(stage = "Wait", candidate = Some(candidate),
          receipts = Vector(TerminatedValidationFixture.receipt(original.state.selected.head)),
          automaticCount = 2, ordinaryCount = 2, generation = 3)
          else _stopped_fixture(original.state, candidate)
        saved <- DelegateTaskNativeRun.saveC(host, store, current, original.copy(state = stopped,
          implementationWorker = Some("SPEC-worker"), implementationWorkers = Some(Vector("SPEC-worker"))), selected)
        _ = { retained = Some(original.copy(state = stopped, implementationWorker = Some("SPEC-worker"),
          implementationWorkers = Some(Vector("SPEC-worker")))); savedhistory = saved.history }
      } yield () }
      // These invalid frontiers are SPEC-owned checkpoint variants. Neither
      // description executes checks; restore the exact stopped business afterward.
      def _replace_stop_(state: S.State): Unit = prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Session is absent")))
        payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
        saved <- DelegateTaskNativeRun.saveC(host, store, current, payload.copy(state = state), selected)
        _ = { savedhistory = saved.history }
      } yield () }
      val originalstop = retained.get.state
      Vector(originalstop.copy(automaticCount = originalstop.repairControl.effectiveLimit),
        originalstop.copy(receipts = originalstop.receipts.map(_.copy(termination = "unknown",
          processObservation = None, externalCapture = None))),
        originalstop.copy(pendingCheck = Some(originalstop.selected.head.identity))).foreach { invalid =>
        _replace_stop_(invalid)
        val response = _call_("describeDelegateTaskRepairContinuation", List(Property("input",
          V.GoalTaskRepairContinuationDescriptionInput(session), None)))
        retained.get.delegateRepairContinuation.filter(_ => invalid.stage != "Human") match {
          case Some(previous) =>
            val collected = read_value[V.GoalTaskRepairContinuationDescriptionResult](_take(response))
            val prior = _take(R.targetC(previous.target.noSpaces))
            collected.outcome shouldBe "already-resumed"
            collected.target shouldBe prior.asJson.noSpaces
            collected.choices shouldBe R.choicesFor(prior)
          case None => response.isSuccess shouldBe false
        }
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
          context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
          selected = setup.core.copy(executionContext = context)
          store <- DelegateTaskNativeRun.storageC(host, definition, selected)
          instances <- store.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Session is absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
          _ = {
            current.history shouldBe savedhistory
            payload.state.automaticCount shouldBe invalid.automaticCount
            payload.state.ordinaryCount shouldBe invalid.ordinaryCount
            payload.state.generation shouldBe invalid.generation
            payload.delegateRepairContinuation shouldBe retained.get.delegateRepairContinuation
          }
        } yield () }
      }
      _replace_stop_(originalstop)
      if (installed.isEmpty) {
        val query = V.GoalTaskRepairContinuationDescriptionInput(session)
        description = Some(read_value[V.GoalTaskRepairContinuationDescriptionResult](_take(_call_("describeDelegateTaskRepairContinuation", List(Property("input", query, None))))))
      }
    }
    if (installed.nonEmpty) description = Some(_installed_call_[V.GoalTaskRepairContinuationDescriptionResult]("describeDelegateTaskRepairContinuation",
      Vector("--input", _encoded_(V.GoalTaskRepairContinuationDescriptionInput(session)))))
    val described = description.get
    described.outcome shouldBe "decision-required"
    val target = _take(R.targetC(described.target))
    target.business shouldBe retained.get
    val reference = V.WorkflowArtifactReference("SPEC-human-direction", "1")
    val direction = V.GoalTaskRepairContinuationInput(session, described.target,
      if (recordedIncomplete) "RepairBeforeRetry" else "ContinueRepair", "SPEC-owned explicit direction", "participant", reference)
    _with_runtime_(root, "TaskTrend", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def _call_(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      _call_("resumeDelegateTaskRepair", List(Property("input", direction, None))).isSuccess shouldBe false
      Vector(direction.copy(sessionId = "SPEC-foreign-session"), direction.copy(participant = "foreign"),
        direction.copy(target = "{}"), direction.copy(target = target.copy(frontierRevision = target.frontierRevision + 1).asJson.noSpaces),
        direction.copy(target = target.copy(componentInstance = "foreign").asJson.noSpaces)).foreach { invalid =>
        _call_("resumeDelegateTaskRepair", List(Property("input", invalid, None))).isSuccess shouldBe false
      }
      val deferreference = V.WorkflowArtifactReference("SPEC-human-defer", "1")
      val deferred = direction.copy(choice = "Defer", reference = deferreference)
      def _provision_(selecteddirection: V.GoalTaskRepairContinuationInput): Unit = {
        val selectedreference = selecteddirection.reference
        val execution = V.WorkflowExecutionEvidence(Vector(selectedreference), Some("participant"), None, None)
        def _save_(name: String, data: org.goldenport.record.RecordPresentable): String = {
          val path = root.resolve("SPEC-" + selectedreference.identity + "-" + name + ".json")
          Files.writeString(path, _encoded_(data))
          path.toUri.toASCIIString
        }
        val requestpath = _save_("request", described)
        val resultpath = _save_("result", selecteddirection)
        val referencepath = _save_("reference", selectedreference)
        val executionpath = _save_("execution", execution)
        val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", "repair-continuation", requestpath, resultpath,
          referencepath, executionpath, "normal-development-host", actor.clock.instant().minusSeconds(1).toString,
          "entity:smworkflow.cli.host_evidence", Some("participant"))
        val row = if (installed.nonEmpty) {
          val record = _installed_record_("prepareEvidence", Vector("--input", _encoded_(preparation),
            "--preparationFormat", "legacy-full"), "NativeWorkflowPreparationService")
          record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        } else {
          val response = _take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "NativeWorkflowPreparationService", operation = "prepareEvidence", properties = List(
              Property("input", preparation, None), Property("preparationFormat", "legacy-full", None))), actor))
          val record = response match {
            case org.goldenport.protocol.operation.OperationResponse.RecordResponse(value) => value
            case org.goldenport.protocol.operation.OperationResponse.Json(value) =>
              _take(new org.goldenport.record.io.RecordDecoder().json(value.noSpaces))
            case other => fail("Expected complete public preparation: " + other)
          }
          record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        }
        prepare(setup => setup.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
      }
      When("independent Human evidence is prepared for Defer and then the exact source-specific repair choice")
      _provision_(deferred)
      read_value[V.GoalTaskRepairContinuationDescriptionResult](_take(_call_("resumeDelegateTaskRepair", List(Property("input", deferred, None))))).outcome shouldBe "deferred"
      val afterdefer = read_value[V.GoalTaskRepairContinuationDescriptionResult](_take(_call_("describeDelegateTaskRepairContinuation",
        List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None)))))
      afterdefer.target shouldBe described.target
      _provision_(direction)
      if (recordedIncomplete) Files.writeString(root.resolve("repository/docs/phase.md"), "Specification-owned compatible source movement\n")
      if (installed.isEmpty) {
        val result = read_value[V.GoalTaskRepairContinuationDescriptionResult](_take(_call_("resumeDelegateTaskRepair", List(Property("input", direction, None)))))
        result.outcome shouldBe "resumed-awaiting-advance"
      }
    }
    if (installed.nonEmpty) _installed_call_[V.GoalTaskRepairContinuationDescriptionResult]("resumeDelegateTaskRepair", Vector("--input", _encoded_(direction))).outcome shouldBe "resumed-awaiting-advance"
    _with_runtime_(root, "TaskTrend", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def _call_(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      Then("reopening and repeating admission appends no extra checkpoint or work and preserves all accounting")
      val repeat = read_value[V.GoalTaskRepairContinuationDescriptionResult](_take(_call_("resumeDelegateTaskRepair", List(Property("input", direction, None)))))
      repeat.outcome shouldBe "already-resumed"
      _call_("resumeDelegateTaskRepair", List(Property("input", direction.copy(rationale = "different"), None))).isSuccess shouldBe false
      var resumed = Option.empty[BoundedWorkRuntime.Payload]
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Session is absent")))
        payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
        _ = { current.history.dropRight(1) shouldBe savedhistory
          payload.copy(state = payload.state.copy(stage = retained.get.state.stage), delegateRepairContinuation = None) shouldBe retained.get
          resumed = Some(payload) }
      } yield () }
      if (installed.isEmpty && !recordedIncomplete) {
        def _replace_current_(business: BoundedWorkRuntime.Payload): Unit = prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
          context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
          selected = setup.core.copy(executionContext = context)
          store <- DelegateTaskNativeRun.storageC(host, definition, selected)
          instances <- store.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Session is absent")))
          _ <- DelegateTaskNativeRun.saveC(host, store, current, business, selected)
        } yield () }
        def _describe_(): V.GoalTaskRepairContinuationDescriptionResult =
          read_value[V.GoalTaskRepairContinuationDescriptionResult](_take(_call_("describeDelegateTaskRepairContinuation",
            List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None)))))
        Given("an actual prior legacy ContinueRepair admission retained in a later specification-owned Wait")
        val state = resumed.get.state
        val ended = state.copy(stage = "Wait", receipts = Vector(TerminatedValidationFixture.receipt(state.selected.head)))
        _replace_current_(resumed.get.copy(state = ended.copy(automaticCount = state.repairControl.effectiveLimit)))
        When("description reads that later exhausted Incomplete Wait")
        val historical = _describe_()
        Then("the original admitted legacy target remains readable without a new direction")
        historical.outcome shouldBe "already-resumed"
        historical.target shouldBe described.target
        historical.choices shouldBe R.choices
        read_value[V.GoalTaskRepairContinuationDescriptionResult](_take(_call_("resumeDelegateTaskRepair",
          List(Property("input", direction, None))))).outcome shouldBe "already-resumed"
        When("the later Wait instead retains normal allowance and an eligible ended Incomplete observation")
        _replace_current_(resumed.get.copy(state = ended))
        val current = _describe_()
        Then("the current Incomplete target takes priority over the historical ContinueRepair admission")
        current.outcome shouldBe "decision-required"
        current.choices shouldBe Vector("RepairBeforeRetry", "Defer")
        current.target should not be described.target
        _take(R.targetC(current.target)).source shouldBe Some(R.Source.RecordedIncomplete)
        _replace_current_(resumed.get)
      }
      if (installed.isEmpty) {
        val issued = read_control_value[V.DelegateTaskView](_take(_call_("advanceDelegateTask", List(Property("sessionId", session, None)))))
        issued.kind shouldBe "Request"
        val reference = issued.retainedWork.get
        val response = _take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
          properties = List(Property("input", reference, None))), actor))
        val detail = read_value[V.WorkflowRetainedWorkReadResult](response)
        val complete = _take(V.DelegateTaskView.createC(detail.response))
        complete.issuedWorkContext.get.requiredOperation shouldBe "delegate-task-repair-capability"
      }
    }
    if (installed.nonEmpty) {
      val issued = _installed_call_[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
      issued.kind shouldBe "Request"
      val reference = issued.retainedWork.get
      val detail = _take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedWorkReadResult]].readC(
        _installed_record_(WorkflowRetainedWork.readOperation, Vector("--input", _encoded_(reference)), WorkflowRetainedWork.serviceName)))
      val complete = _take(V.DelegateTaskView.createC(detail.response))
      complete.issuedWorkContext.get.requiredOperation shouldBe "delegate-task-repair-capability"
    }
    Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
    Files.readString(root.resolve("repository/docs/phase.md")) shouldBe (if (recordedIncomplete)
      "Specification-owned compatible source movement\n" else "Specification-owned original source\n")
  }
  "Managed DelegateTask repair" should {
    "preserve the exact stopped Session across admission, separate Advance and reopen" in { qualify(None) }
    "issue ordinary Repair after a recorded Incomplete direction and compatible owned source movement" in {
      qualify(None, recordedIncomplete = true)
    }
  }
}
