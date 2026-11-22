package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.record.{Record, RecordPresentable}
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import NativeCommonDelegateTaskFixture as C
import SplitPhaseExplicitPartitionFixture.take
import StandaloneFixExecution as S
import S.given

/** An explicitly selected, two-installation qualification of one physical native
  * requester. Transport, Human direction and review judgments are specification
  * owned. CLI intake, checks, commit and publication are actual runtime effects.
  * This does not represent a Codex UI chat delivery or consumer authorization.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskRequestReconstructionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _primary = Path.of("").toAbsolutePath.normalize
  private lazy val _executable = _selected_path("smworkflow.spec.installedCommand", "SM_WORKFLOW_SPEC_INSTALLED_COMMAND", executable = true)
  private lazy val _component = _selected_path("smworkflow.spec.componentFile", "SM_WORKFLOW_SPEC_COMPONENT_FILE")
  private lazy val _episode = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.requestReconstructionEpisode",
      fail("Select an absolute smworkflow.spec.requestReconstructionEpisode under this repository target")))
    require(path.isAbsolute && path.normalize == path && path.startsWith(_primary.resolve("target")),
      "Keep the explicit episode locator under the existing repository target")
    path
  }

  private def _selected_path(property: String, environment: String, executable: Boolean = false): Path = {
    val path = Path.of(sys.props.get(property).orElse(sys.env.get(environment)).getOrElse(fail("Select " + property)))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path) &&
      (!executable || Files.isExecutable(path)), "Select the actual absolute installed file: " + property)
    path
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _json(value: Record): String = take(value.toJsonStringC)
  private def _parse(text: String): Json = parser.parse(text).fold(error => fail(error.message), identity)
  private def _read(path: Path): Json = _parse(Files.readString(path))
  private def _state(view: V.DelegateTaskView): S.State =
    GoalPhaseNativeMetadata.normalizePublicJson(_parse(_json(view.state.get))).toOption.get.as[S.State].toOption.get
  private def _work(view: V.DelegateTaskView): S.Work =
    GoalPhaseNativeMetadata.normalizePublicJson(_parse(_json(view.work.get))).toOption.get.as[S.Work].toOption.get
  private def _call(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    scenario.callInstalled(_executable, service, operation, arguments,
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
  private def _view(scenario: F.Scenario, result: F.Result): V.DelegateTaskView = C.view(scenario, result, _call)
  private def _advance(scenario: F.Scenario, session: String): V.DelegateTaskView =
    _view(scenario, _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", session)))
  private def _save(root: Path, name: String, value: RecordPresentable): String = {
    val path = root.resolve(name + ".json")
    Files.writeString(path, _json(value))
    path.toUri.toASCIIString
  }
  private def _reference(path: Path): V.WorkflowArtifactReference = V.WorkflowArtifactReference(path.toUri.toASCIIString, "1")

  private def _request(scenario: F.Scenario): V.DelegateTaskInput = {
    val authority = scenario.root.resolve("SPECIFICATION-OWNED-original-authority.json")
    Files.writeString(authority, Json.obj("owner" -> "SPECIFICATION-OWNED".asJson,
      "authority" -> "Tiny fixture behavior, review, local commit and local publication only".asJson).spaces2)
    V.DelegateTaskInput(scenario.host.goal.input.repositoryIdentity, "SPECIFICATION-OWNED-request-reconstruction",
      "SPECIFICATION-OWNED author tiny LineWriter behavior and return its actual reviewed committed published result",
      NonEmptyVector.one(C.testPath), NonEmptyVector.of("compile", "fix-check", "test-only"),
      NonEmptyVector.of("behavior", "validation", "review", "commit", "publication", "return"),
      NonEmptyVector.one(_reference(authority)), true, "code", "required", 10, "direct-instruction", Vector.empty,
      V.DelegateTaskContract("SPECIFICATION-OWNED-requester", "SPECIFICATION-OWNED-destination",
        "SPECIFICATION-OWNED-requester", C.component, "requester", "localized",
        NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt", "publishLocal"),
        Vector("remote-push", "consumer-activation"), NonEmptyVector.one(_reference(authority)), Some(C.publication),
        Vector("org.example.sm-workflow-common-delegate-fixture:common-delegate-fixture_3:0.0.1-SNAPSHOT"),
        "SPECIFICATION-OWNED tiny artifact result; consumer selection and activation remain separate", None, None, None), Vector.empty)
  }

  private def _prepare_start(scenario: F.Scenario, input: V.DelegateTaskInput, admission: String = "original"): F.Result = {
    val role = input.delegation.role
    val resource = _save(scenario.root, role + "-" + admission + "-input", input)
    val prepared = _call(scenario, "NativeWorkflowPreparationService", "prepareDelegateTaskStart", Vector("--input", _json(
      V.WorkflowDelegateTaskStartPreparationInput(resource, role + "-" + admission + "-selection", "participant", "ExplicitHuman",
        role + "-" + admission + "-key", "entity:smworkflow.cli.goal_selection"))))
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    scenario.selectPreparedImport(prepared)
    prepared
  }
  private def _start(scenario: F.Scenario, input: V.DelegateTaskInput, admission: String = "original"): F.Result =
    _call(scenario, "DelegateTaskService", "startDelegateTask", Vector("--input", _json(input),
      "--selectionId", input.delegation.role + "-" + admission + "-selection", "--participant", "participant",
      "--idempotencyKey", input.delegation.role + "-" + admission + "-key"))

  private def _capture_old(): Unit = {
    Given("one isolated tiny normal host and a locator that has never captured a successful episode")
    Files.exists(_episode) shouldBe false
    val scenario = C.fresh(Some(_component))
    val request = _request(scenario)
    val preparation = _prepare_start(scenario, request)
    When("the old installed requester retains and dispatches the complete original request")
    val started = _view(scenario, _start(scenario, request))
    val dispatchresult = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", started.sessionId))
    val dispatch = _view(scenario, dispatchresult)
    val original = _state(dispatch)
    Then("the passive requester retains the erroneous destination requirements without executing them")
    dispatch.kind shouldBe "Dispatch"
    original.stage shouldBe "DispatchReady"
    original.input.checks shouldBe Vector("compile", "fix-check", "test-only")
    original.input.scalaLint shouldBe "required"
    original.selected shouldBe empty
    original.receipts shouldBe empty
    Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
    val basehead = scenario.candidateHead
    val destinationinput = request.copy(delegation = request.delegation.copy(role = "destination"))
    And("the actual old payload is delivered through this fixture's normal CLI intake transport")
    val destinationpreparation = _prepare_start(scenario, destinationinput)
    val refused = _start(scenario, destinationinput)
    Then("the destination handler terminates with a genuine pre-Start missing check binding refusal")
    refused.exitCode should not be 0
    val diagnostic = Files.readString(refused.invocation.stdout) + Files.readString(refused.invocation.stderr)
    diagnostic.toLowerCase should include("fix-check")
    ProcessHandle.of(refused.invocation.pid).isPresent shouldBe false
    Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
    Files.exists(scenario.root.resolve("publication-count.txt")) shouldBe false
    _state(_advance(scenario, started.sessionId)) shouldBe original
    val report = scenario.root.resolve("SPECIFICATION-OWNED-intake-terminal.json")
    Files.writeString(report, Json.obj("owner" -> "SPECIFICATION-OWNED CLI intake transport; no Codex UI chat claim".asJson,
      "destinationIdentity" -> request.delegation.destinationIdentity.asJson,
      "notificationStatus" -> "delivered".asJson, "intakeStatus" -> "rejected-before-start".asJson,
      "handlerStatus" -> "ended".asJson, "exitCode" -> refused.exitCode.asJson,
      "pid" -> refused.invocation.pid.asJson, "destinationSessionId" -> Json.Null,
      "input" -> scenario.root.resolve("destination-original-input.json").toUri.toASCIIString.asJson,
      "stdout" -> refused.invocation.stdout.toUri.toASCIIString.asJson,
      "stderr" -> refused.invocation.stderr.toUri.toASCIIString.asJson).spaces2)
    val locator = Json.obj("schema" -> "SPECIFICATION-OWNED-request-reconstruction-episode-v1".asJson,
      "root" -> scenario.root.toString.asJson, "session" -> started.sessionId.asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
      "baseHead" -> basehead.asJson,
      "request" -> _parse(_json(request)), "originalState" -> original.asJson,
      "dispatchControl" -> _read(dispatchresult.invocation.stdout),
      "requesterPreparation" -> preparation.invocation.stdout.toString.asJson,
      "destinationPreparation" -> destinationpreparation.invocation.stdout.toString.asJson,
      "intakeReport" -> report.toString.asJson, "refusalStdout" -> refused.invocation.stdout.toString.asJson,
      "refusalStderr" -> refused.invocation.stderr.toString.asJson,
      "nextLogIndex" -> (scenario.invocations.map(_.stdout.getFileName.toString.stripSuffix(".stdout.json").toInt).max + 1).asJson,
      "oldComponent" -> _component.toString.asJson)
    Files.createDirectories(_episode.getParent)
    Files.writeString(_episode, locator.spaces2, StandardOpenOption.CREATE_NEW)
    info("Old requester and actual ended intake retained at " + _episode)
  }

  private def _prepare_evidence(scenario: F.Scenario, kind: String, request: RecordPresentable,
    result: RecordPresentable, reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    participant: Option[String]): Unit = {
    val prefix = "SPECIFICATION-OWNED-" + kind
    val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", kind,
      _save(scenario.root, prefix + "-request", request), _save(scenario.root, prefix + "-result", result),
      _save(scenario.root, prefix + "-reference", reference), _save(scenario.root, prefix + "-execution", execution),
      "normal-development-host", Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence", participant)
    val prepared = _call(scenario, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation)))
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    scenario.selectPreparedImport(prepared)
  }

  private def _submit(scenario: F.Scenario, view: V.DelegateTaskView, result: V.DelegateTaskWorkResult,
    name: String, independentreview: Boolean = false): V.DelegateTaskView = {
    val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + name, "1")
    val worker = "SPECIFICATION-OWNED-" + name
    val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None,
      Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", worker, "ordinary")))
    if (independentreview) _prepare_evidence(scenario, "review", view, result, reference, evidence, None)
    _view(scenario, _call(scenario, "DelegateTaskService", "submitDelegateTaskWorkResult", Vector("--sessionId", view.sessionId,
      "--issuedWorkContext", _json(view.issuedWorkContext.get), "--result", _json(result),
      "--resultReference", _json(reference), "--executionEvidence", _json(evidence))))
  }

  private def _observe(scenario: F.Scenario, session: String, event: String, destination: String,
    result: Option[Record] = None): V.DelegateTaskView =
    _view(scenario, _call(scenario, "DelegateTaskDeliveryService", "observeDelegateTaskDelivery", Vector("--input", _json(
      V.DelegateTaskObservationInput(session, event, V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + event, "1"),
        Some(destination), result, None, "SPECIFICATION-OWNED actual fixture CLI result transport; no consumer effects", None)))))

  private def _continue_new(): Unit = {
    Given("the exact old requester, its native selection, original payload, dispatch and terminated intake captures")
    Files.isRegularFile(_episode) shouldBe true
    val locator = _read(_episode)
    val cursor = locator.hcursor
    cursor.get[String]("schema").toOption.get shouldBe "SPECIFICATION-OWNED-request-reconstruction-episode-v1"
    val root = Path.of(cursor.get[String]("root").toOption.get)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(cursor.get[Json]("host").toOption.get))
    val plan = cursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get
    val scenario = F.reopenInstalledRepository(root, host, plan, cursor.get[Int]("nextLogIndex").toOption.get)
    val session = cursor.get[String]("session").toOption.get
    val original = cursor.get[S.State]("originalState").toOption.get
    val request = take(V.DelegateTaskInput.createC(GoalPhaseNativeMetadata.record(cursor.get[Json]("request").toOption.get)))
    val report = Path.of(cursor.get[String]("intakeReport").toOption.get)
    val intake = _read(report)
    intake.hcursor.get[String]("handlerStatus").toOption.get shouldBe "ended"
    intake.hcursor.get[String]("intakeStatus").toOption.get shouldBe "rejected-before-start"
    intake.hcursor.get[Int]("exitCode").toOption.get should not be 0
    Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
    // Normal candidate archive selection is host configuration. No native
    // Session, input, selection, store, database or history is rewritten here.
    val configfile = root.resolve(".textus/config.json")
    Files.writeString(configfile, _read(configfile).mapObject(_.add("textus.component.file", _component.toString.asJson)).noSpaces)
    When("the new normally installed CLI describes the SAME unstarted requester through its session-only public operation")
    val description = _call(scenario, "DelegateTaskService", "describeDelegateTaskRequestReconstruction",
      Vector("--sessionId", session)).business[V.DelegateTaskRequestReconstructionDescriptionResult]
    Then("the target identifies the original policy and offers the explicit reconstruction direction")
    description.sessionId shouldBe session
    description.choices should contain("AdoptDerivedValidationPolicy")
    val target = _parse(description.target)
    target.hcursor.get[String]("sessionId").toOption.get shouldBe session
    target.hcursor.get[String]("requestIdentity").toOption.get shouldBe request.requestIdentity
    val before = _advance(scenario, session)
    _state(before) shouldBe original
    val beforehead = scenario.candidateHead
    beforehead shouldBe cursor.get[String]("baseHead").toOption.get
    val humanfile = root.resolve("SPECIFICATION-OWNED-actual-Human-direction.json")
    val humanreference = _reference(humanfile)
    val sources = NonEmptyVector.of(_reference(root.resolve("destination-original-input.json")), _reference(report),
      _reference(Path.of(cursor.get[String]("refusalStdout").toOption.get)),
      _reference(Path.of(cursor.get[String]("refusalStderr").toOption.get)))
    val observation = V.DelegateTaskIntakeObservation(request.delegation.destinationIdentity, "delivered",
      "rejected-before-start", "ended", None, sources)
    val direction = V.DelegateTaskRequestReconstructionInput(session, description.target,
      NonEmptyVector.of("compile", "test-only"), "off", "AdoptDerivedValidationPolicy",
      "SPECIFICATION-OWNED actual Human chooses the tiny fixture Compile and fixed TestOnly policy, with lint off; " +
        "the supplied old CLI input and actual terminal refusal establish that the destination handler ended before Start",
      "participant", humanreference, observation)
    Files.writeString(humanfile, _json(direction), StandardOpenOption.CREATE_NEW)
    val humanexecution = V.WorkflowExecutionEvidence(Vector(humanreference), Some("participant"), None, None)
    When("an independent fixture-owned Human direction binds the exact target, corrected policy and actual ended intake sources")
    _prepare_evidence(scenario, "request-reconstruction", description, direction, humanreference, humanexecution, Some("participant"))
    def _reconstruct_() = _call(scenario, "DelegateTaskService", "reconstructDelegateTaskRequest", Vector("--input", _json(direction)))
      .business[V.DelegateTaskRequestReconstructionDescriptionResult]
    val reconstructed = _reconstruct_()
    Then("reconstruction waits for ordinary Advance and recollects the same admitted direction without another effect")
    reconstructed.outcome shouldBe "reconstructed-awaiting-advance"
    val recollected = _reconstruct_()
    recollected.outcome shouldBe "already-reconstructed"
    val afterrecollect = _call(scenario, "DelegateTaskService", "describeDelegateTaskRequestReconstruction", Vector("--sessionId", session))
      .business[V.DelegateTaskRequestReconstructionDescriptionResult]
    afterrecollect.sessionId shouldBe session
    Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
    Files.exists(root.resolve("publication-count.txt")) shouldBe false
    scenario.candidateHead shouldBe beforehead
    // Resolve the old references again, using only the original public capture.
    val oldcontrol = take(V.DelegateTaskView.createC(GoalPhaseNativeMetadata.record(cursor.get[Json]("dispatchControl").toOption.get)))
    oldcontrol.retainedMaterials should not be empty
    oldcontrol.retainedMaterials.foreach { field =>
      val material = _call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedSnapshot.readOperation,
        Vector("--input", _json(field.reference))).business[V.WorkflowRetainedSnapshotReadResult]
      if (field.fieldName == "state") {
        val historic = GoalPhaseNativeMetadata.normalizePublicJson(_parse(_json(material.material))).toOption.get.as[S.State].toOption.get
        historic shouldBe original
      }
    }
    When("ordinary Advance emits the corrected typed dispatch input from that retained requester")
    val dispatch = _advance(scenario, session)
    val corrected = _state(dispatch)
    val destinationinput = dispatch.dispatchInput.getOrElse(fail("Dispatch must supply its canonical typed destination input"))
    _reconstruct_().outcome shouldBe "already-reconstructed"
    val repeateddispatch = _advance(scenario, session)
    Then("only checks and lint change while business authority, timing, results and every existing counter are preserved")
    dispatch.kind shouldBe "Dispatch"
    dispatch.sessionId shouldBe session
    repeateddispatch.retainedMaterials shouldBe dispatch.retainedMaterials
    _state(repeateddispatch) shouldBe corrected
    corrected.input.copy(checks = original.input.checks, scalaLint = original.input.scalaLint) shouldBe original.input
    corrected.copy(input = original.input, message = original.message) shouldBe original
    destinationinput shouldBe request.copy(checks = NonEmptyVector.of("compile", "test-only"), scalaLint = "off",
      delegation = request.delegation.copy(role = "destination"))
    And("the destination makes its first accepted Start through its normal prepared selection and complete input binding")
    _prepare_start(scenario, destinationinput, "corrected")
    val destination = _view(scenario, _start(scenario, destinationinput, "corrected"))
    destination.sessionId should not be session
    _observe(scenario, session, "dispatch-observed", destination.sessionId)
    val implement = _advance(scenario, destination.sessionId)
    _work(implement).kind shouldBe "Implement"
    val path = Path.of(host.target.root).resolve(C.testPath)
    Files.createDirectories(path.getParent)
    Files.writeString(path, """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |import org.scalatest.matchers.should.Matchers
      |import org.scalatest.GivenWhenThen
      |final class LineWriterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
      |  "LineWriter" should { "retain each selected value before one terminator" in {
      |    Given("ASCII Unicode and empty text")
      |    val inputs = Vector("expected", "日本語", "")
      |    When("each selected value is rendered")
      |    val rendered = inputs.map(LineWriter.render)
      |    Then("each original text precedes exactly one LF")
      |    rendered shouldBe inputs.map(_ + "\n")
      |  }}
      |}
      |""".stripMargin)
    val pending = request.conditions.toVector.map(id => V.DelegateTaskCondition(id, false,
      "SPECIFICATION-OWNED obligation awaits actual runtime validation review commit publication and return judgment"))
    val completed = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED tiny behavior supplied for actual verification",
      Vector.empty, NonEmptyVector.fromVectorUnsafe(pending), Vector.empty, None, None)
    _submit(scenario, implement, completed, "implementer")
    When("normal destination Advance performs its selected checks and requests an independent review")
    var current = _advance(scenario, destination.sessionId)
    var remaining = 12
    while (current.work.isEmpty && current.reviewContext.isEmpty && current.kind != "Return" && remaining > 0) {
      current = _advance(scenario, destination.sessionId)
      remaining -= 1
    }
    Then("the review sees genuine successful Compile and fixed TestOnly receipts")
    withClue(current.message) { current.reviewContext should not be empty }
    _work(current).kind shouldBe "Review"
    _state(current).receipts.map(_.identity) shouldBe Vector("compile", "test-only")
    _state(current).receipts.foreach(_.exitCode shouldBe Some(0))
    val reviewed = completed.copy(outcome = "accept", summary = "SPECIFICATION-OWNED independent reviewer accepts tiny behavior and actual selected checks")
    _submit(scenario, current, reviewed, "independent-reviewer", independentreview = true)
    And("ordinary destination owners create the tiny source commit and publish its actual local artifact")
    current = _advance(scenario, destination.sessionId)
    remaining = 12
    while (current.work.isEmpty && current.kind != "Return" && remaining > 0) {
      current = _advance(scenario, destination.sessionId)
      remaining -= 1
    }
    Then("DeliveryAcceptance binds all six original obligations to those retained actual effects")
    _work(current).kind shouldBe "DeliveryAcceptance"
    val accepted = _state(current)
    accepted.reviewReference should not be empty
    accepted.commit.get.identity shouldBe scenario.candidateHead
    scenario.candidateHead should not be beforehead
    accepted.delivery.get.publication.get.ready shouldBe true
    accepted.delivery.get.publication.get.request.source.head shouldBe scenario.candidateHead
    Files.readString(root.resolve("publication-count.txt")).linesIterator.toVector shouldBe Vector("published")
    Files.readString(root.resolve("sbt-command-lines.txt")) should include("testOnly " + C.suite)
    val checks = Files.readString(root.resolve("sbt-executions.txt"))
    val publications = Files.readString(root.resolve("publication-count.txt"))
    val finalresult = completed.copy(outcome = "accept", conditions = NonEmptyVector.fromVectorUnsafe(pending.map(_.copy(
      satisfied = true, detail = "SPECIFICATION-OWNED final judgment covers the retained checks independent review source commit local publication and return"))))
    _submit(scenario, current, finalresult, "delivery-acceptance")
    When("the actual producer ReturnReady result is transported back to the SAME current corrected requester")
    val returned = _advance(scenario, destination.sessionId)
    returned.kind shouldBe "Return"
    _state(returned).stage shouldBe "ReturnReady"
    val received = _observe(scenario, session, "result-received", destination.sessionId, returned.state)
    Then("strict full-request matching accepts that actual corrected producer without replaying effects")
    received.sessionId shouldBe session
    _state(received).stage shouldBe "ResultReceived"
    _state(received).input shouldBe corrected.input
    _state(returned).conditionReport.map(_.identity) shouldBe request.conditions.toVector
    _state(returned).conditionReport.forall(_.satisfied) shouldBe true
    _state(_observe(scenario, session, "result-received", destination.sessionId, returned.state)) shouldBe _state(received)
    Files.readString(root.resolve("sbt-executions.txt")) shouldBe checks
    Files.readString(root.resolve("publication-count.txt")) shouldBe publications
    scenario.candidateHead shouldBe accepted.commit.get.identity
    scenario.candidateStatus shouldBe ""
    info("Same requester qualification completed; public captures retained under " + root)
  }

  "Installed requester reconstruction" should {
    "retain one old intake refusal or continue that same episode with the new installation" in {
      sys.props.get("smworkflow.spec.requestReconstructionMode") match {
        case Some("capture-old") => _capture_old()
        case Some("continue-new") => _continue_new()
        case _ => fail("Select smworkflow.spec.requestReconstructionMode=capture-old or continue-new")
      }
    }
  }
}
