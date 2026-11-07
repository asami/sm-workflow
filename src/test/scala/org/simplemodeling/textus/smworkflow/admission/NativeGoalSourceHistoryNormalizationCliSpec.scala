package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path}
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.protocol.Request
import org.goldenport.record.RecordPresentable
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseSourceHistoryNormalization as N
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Every native row comes from normal Operations in a fresh test-owned host.
  * Explicit installed mode pins the immutable publication directory once; an
  * optional old image creates legacy questions without changing stored rows.
  * Host policy/lifecycle and reviewer evidence here are specification-owned.
  * @since   Oct. 26, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryNormalizationCliSpec extends NativeGoalSourceHistoryRecoveryCliSupport
    with ThreeWorkflowManagedRepositoryFixture {
  private lazy val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
  private lazy val installed = sys.props.get("smworkflow.spec.installedCommand").map { value =>
    val path = Path.of(value)
    require(path.isAbsolute && Files.isExecutable(path), "installedCommand requires an absolute existing executable")
    path.toRealPath()
  }
  private lazy val currentImage = installed.map { _ =>
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.runtimeRoot",
      throw new IllegalArgumentException("Installed normalization qualification requires smworkflow.spec.runtimeRoot")))
    require(path.isAbsolute && Files.isDirectory(path), "runtimeRoot requires an absolute immutable publication directory")
    path.toRealPath()
  }
  private var selectedImage = Option.empty[Path]
  private var _current_wait_append = false
  private var _current_wait_advance = Option.empty[F.Result]

  override def configure(scenario: F.Scenario, settings: Map[String, Json]): Unit = {
    val selected = if (_current_wait_append) sys.props.get("smworkflow.spec.componentFile")
      .fold(settings)(value => settings.updated("textus.component.file", Json.fromString(value))) else settings
    super.configure(scenario, selected)
  }

  override protected def _call(fixture: Implemented, service: String, operation: String,
    arguments: Vector[String]): F.Result = {
    val result = if (_current_wait_append) installed match {
      case Some(executable) => fixture.scenario.callInstalled(executable, service, operation, arguments,
        assemblyDescriptor = Some(fixture.scenario.root.resolve(".textus/assembly.yaml")))
      case None => super._call(fixture, service, operation, arguments)
    } else installed match {
    case None => super._call(fixture, service, operation, arguments)
    case Some(executable) =>
      val image = selectedImage.orElse(currentImage).get
      val logs = Files.createTempDirectory(fixture.scenario.root, "normalization-installed-")
      val stdout = logs.resolve("response.json")
      val stderr = logs.resolve("stderr.log")
      val argv = Vector(executable.toString, "--runtime-root", fixture.scenario.root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")
      val builder = new ProcessBuilder(argv.asJava).directory(fixture.scenario.root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", image.toString)
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) { process.waitFor(240, TimeUnit.SECONDS) shouldBe true }
        F.Result(process.exitValue(), F.Invocation(argv, process.pid(), stdout, stderr))
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
          process.waitFor()
        }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }
    if (_current_wait_append && service == "GoalPhaseService" && operation == "advanceGoalPhase")
      _current_wait_advance = Some(result)
    result
  }

  private final case class Pending(episode: DocumentEpisode, question: V.GoalPhaseAdvanceResult) {
    def fixture: Implemented = episode.fixture
  }
  private def pending(name: String, legacyImage: Boolean = false): Pending = {
    selectedImage = if (legacyImage && installed.nonEmpty) sys.props.get("smworkflow.spec.legacyRuntimeRoot").map { value =>
      val root = Path.of(value)
      require(root.isAbsolute && Files.isDirectory(root), "legacyRuntimeRoot requires the actual old immutable published image")
      root.toRealPath()
    } else None
    val episode = _document_episode(name, later = true)
    val issued = _advance(episode.fixture).business[V.GoalPhaseAdvanceResult]
    issued.kind shouldBe "Human"
    issued.human.get.choices shouldBe GoalPhaseSourceHistoryRecovery.choices
    val recovery = take(GoalPhaseSourceHistoryRecovery.targetC(issued.human.get.sourceHistoryRecovery.get))
    recovery.intentIndex shouldBe 4
    recovery.completedPrefix.size shouldBe 4
    selectedImage = currentImage
    Pending(episode, issued)
  }
  private def describe(fixture: Implemented): F.Result =
    _goal(fixture, N.describeOperation, Vector("--sessionId", fixture.request.sessionId))
  private def direction(fixture: Implemented, described: F.Result, choice: String = N.choices.head,
    ended: Boolean = true, provision: Boolean = true,
    held: Option[V.WorkflowArtifactReference] = None): V.GoalPhaseSourceHistoryNormalizationAdoptionInput = {
    val description = described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
    val directory = Files.createTempDirectory(fixture.scenario.root, "actual-test-policy-direction-")
    val reference = held.getOrElse(V.WorkflowArtifactReference(directory.getFileName.toString +
      (if (_current_wait_append) ":specification-held-policy" else ":already-held-oct26-policy"), "1"))
    val answer = V.GoalPhaseSourceHistoryNormalizationAnswer(choice,
      if (_current_wait_append) "Specification-owned held policy and actual ended invocation; the unsuspended Wait has no Human answer"
      else "Fixture-owned existing policy direction and actual completed old invocation; this does not answer the pending Human", ended)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    def file(name: String, value: RecordPresentable): String = {
      val path = directory.resolve(name + ".json")
      Files.writeString(path, _json(value))
      path.toUri.toASCIIString
    }
    if (provision) {
      val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "source-history-normalization",
        described.invocation.stdout.toUri.toASCIIString, file("answer", answer), file("reference", reference),
        file("execution", execution), "normal-development-host",
        if (_current_wait_append) java.time.Instant.now().toString else "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", Some("participant"))
      _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation))))
    }
    V.GoalPhaseSourceHistoryNormalizationAdoptionInput(description.sessionId, description.target, choice,
      answer.rationale, reference, ended)
  }
  private def adopt(fixture: Implemented, input: V.GoalPhaseSourceHistoryNormalizationAdoptionInput): F.Result =
    _goal(fixture, N.adoptionOperation, Vector("--input", _json(input)))
  private def oldResultAbsent(fixture: Implemented, target: N.Target): Unit = {
    val results = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, catalog,
      GoalPhaseNativeSourceHistory.codec).toOption)
    results.exists(_.reference.identity == target.recovery.sessionId + ":ordered-candidate:" +
      target.recovery.sourceRevision + ":source-history-result-" + target.recovery.intentIndex) shouldBe false
  }
  private def observation(fixture: Implemented, target: N.Target): N.Observation =
    _receipt(fixture, WorkflowMechanicalTransition.Reference(N.reference(target).identity + ":reconciliation-result", "1"),
      N.observationCodec).payload.value
  private def _normalization_facts(fixture: Implemented): Vector[String] = _stored_json(fixture).filter { raw =>
    parse(raw).toOption.exists(_.hcursor.downField("payload").get[String]("typeIdentity").toOption.exists(kind =>
      Set(N.codec.typeIdentity, N.observationCodec.typeIdentity)(kind)))
  }
  private def _reconciliation_result_absent(fixture: Implemented, target: N.Target): Unit = {
    val reference = WorkflowMechanicalTransition.Reference(N.reference(target).identity + ":reconciliation-result", "1")
    _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, catalog, N.observationCodec).toOption)
      .exists(_.reference == reference) shouldBe false
  }
  private def corrected(fixture: Implemented, target: N.Target, normalized: Boolean): Unit = {
    val text = "// Preserve this independent source comment.\n" + _later_corrected_text
    val selected = if (!normalized) text else take(WorkflowSourceHistoryRule.applyC(
      take(WorkflowSourceHistoryRule.decodeC(target.recovery.historyPolicy)), WorkflowSourceHistoryRule.Input(sourcePath,
        text, LocalDate.parse(target.recovery.intent.updatedOn), None, None, target.recovery.intent.dateUncertain))).text
    Files.writeString(fixture.source, selected)
  }

  /** Current unsuspended public normalization, isolated from historical Human
    * qualification and its old installed launcher contract. */
  def qualifyCurrentWaitAppend(reporter: org.scalatest.GivenWhenThen): Path = {
    require(!_current_wait_append, "The current Wait fixture is already active")
    if (installed.nonEmpty) {
      val component = Path.of(sys.props.getOrElse("smworkflow.spec.componentFile",
        fail("Current installed qualification requires the normally selected componentFile")))
      require(component.isAbsolute && Files.isRegularFile(component), "Select the actual published component CAR")
    }
    val previous = _current_wait_advance
    _current_wait_append = true
    try {
      sys.props.get("smworkflow.spec.currentWaitAppendResumeInput") match {
        case Some(value) => _resume_current_wait_append(reporter, Path.of(value))
        case None =>
          reporter.Given("R5-SH04: a fresh specification-owned two-Step Session, accepted Step1, four completed history updates and a later UNKNOWN File intent")
          val episode = _document_episode("normalization-current-wait-append", later = true)
          val fixture = episode.fixture
          val oldcall = _current_wait_advance.getOrElse(fail("The actual failed old invocation is absent"))
          val repository = Path.of(fixture.scenario.host.target.root)
          val before = _current(fixture)
          val originalbytes = fileBytes(fixture.source)
          val originalindex = indexBytes(fixture.scenario)
          val completedfacts = _completed_facts(fixture)
          val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)),
            Files.getLastModifiedTime(repository.resolve(path))))
          reporter.When("ordinary current Advance exposes its actual unsuspended Wait and the normal public owner describes that frontier")
          val waiting = _advance(fixture).business[V.GoalPhaseAdvanceResult]
          val described = describe(fixture)
          val description = described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
          val target = take(N.targetC(description.target))
          val current = _current(fixture)
          val unknown = _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity,
            target.recovery.intentRevision), GoalPhaseNativeSourceHistory.codec)
          val checks = executions(fixture.scenario)
          reporter.Then("the failed process has ended and the real Wait retains its completed prefix without inventing a Human")
          oldcall.exitCode should not be 0
          waiting.kind shouldBe "Wait"
          waiting.human shouldBe None
          before.suspension shouldBe None
          current shouldBe before
          target.pendingQuestion shouldBe None
          target.recovery.intentIndex shouldBe 4
          target.recovery.completedPrefix.size shouldBe 4
          unknown.payload.value.outcome shouldBe None
          episode.completed.work.get.completed.map(_.step) shouldBe Vector("step-1")
          checks.size shouldBe 1

          reporter.Given("independently prepared specification-only held policy direction attesting the actual completed old invocation")
          val input = direction(fixture, described, ended = true)
          reporter.When("the public adoption passively stores the exact direction on the unsuspended frontier")
          val adoption = adopt(fixture, input).business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult]
          val adopted = _current(fixture)
          val adoptedbytes = fileBytes(fixture.source)
          val adoptedindex = indexBytes(fixture.scenario)
          val adoptedfacts = _completed_facts(fixture)
          val adoptedchecks = executions(fixture.scenario)
          reporter.Then("only the native direction advances while source index completed Step facts and actual check count remain literal")
          adoption.outcome shouldBe "adopted"
          adopted.history.dropRight(1) shouldBe before.history
          adopted.causation.value shouldBe N.cause(target)
          adopted.suspension shouldBe None
          adoptedbytes shouldBe originalbytes
          adoptedindex shouldBe originalindex
          adoptedfacts shouldBe completedfacts
          adoptedchecks shouldBe checks

          reporter.When("the fixture corrects the malformed source comment and a separate ordinary Advance reconciles and checks it")
          corrected(fixture, target, normalized = false)
          val reviewed = _advance(fixture).business[V.GoalPhaseAdvanceResult]
          val reconciled = observation(fixture, target)
          val reviewedchecks = executions(fixture.scenario)
          val reviewedprefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)),
            Files.getLastModifiedTime(repository.resolve(path))))
          val retainedunknown = _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity,
            target.recovery.intentRevision), GoalPhaseNativeSourceHistory.codec)
          val oldresult = target.recovery.sessionId + ":ordered-candidate:" + target.recovery.sourceRevision +
            ":source-history-result-" + target.recovery.intentIndex
          val oldresultpresent = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw,
            catalog, GoalPhaseNativeSourceHistory.codec).toOption).exists(_.reference.identity == oldresult)
          reporter.Then("real selected SBT succeeds and independent ReviewStep retains accepted work counters and the original UNKNOWN")
          reconciled.outcome shouldBe Some("updated-current")
          reviewed.work.get.kind shouldBe "ReviewStep"
          reviewed.work.get.stepIdentity shouldBe Some("step-2")
          reviewed.work.get.originalBase shouldBe episode.completed.work.get.originalBase
          reviewed.work.get.selectedPlan shouldBe episode.completed.work.get.selectedPlan
          reviewed.work.get.completed shouldBe episode.completed.work.get.completed
          reviewed.repairProgress shouldBe episode.completed.repairProgress
          reviewedchecks.size shouldBe 2
          reviewedprefix shouldBe prefix
          retainedunknown shouldBe unknown
          oldresultpresent shouldBe false

          _current_wait_append_suffix(reporter, fixture, target, reviewed, resumed = false, oldexit = Some(oldcall.exitCode))
      }
    } finally {
      _current_wait_append = false
      _current_wait_advance = previous
    }
  }

  private def _current_wait_state(fixture: Implemented, target: N.Target) =
    (_current(fixture), _stored_json(fixture).sorted, _completed_facts(fixture),
      _normalization_facts(fixture).sorted, observation(fixture, target),
      _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity,
        target.recovery.intentRevision), GoalPhaseNativeSourceHistory.codec),
      fileBytes(fixture.source), Files.getLastModifiedTime(fixture.source), indexBytes(fixture.scenario),
      executions(fixture.scenario), _prefix_paths.map(path => (path,
        fileBytes(Path.of(fixture.scenario.host.target.root).resolve(path)),
        Files.getLastModifiedTime(Path.of(fixture.scenario.host.target.root).resolve(path)))))

  private def _current_wait_append_suffix(reporter: org.scalatest.GivenWhenThen, fixture: Implemented,
    target: N.Target, reviewed: V.GoalPhaseAdvanceResult, resumed: Boolean, oldexit: Option[Int]): Path = {
    val retained = _current_wait_state(fixture, target)
    val direction = _receipt(fixture, N.reference(target), N.codec).payload.value
    val config = fixture.scenario.root.resolve(".textus/config.json")
    val originalconfig = Files.readAllBytes(config)
    val parameters = parse(Files.readString(config)).toOption.get
    val key = "sm-workflow.runtime.definition"
    val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(parameters.hcursor.get[String](key).toOption.get).toOption.get))
    val paths = definition.goal.candidatePaths
    val extendedpaths = paths :+ "src/main/scala/RetainedCurrentWaitUnused.scala"
    def _declare_(selected: Vector[String]): Unit = configure(fixture.scenario,
      enabledSettings.updated(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = selected))).noSpaces)))
    target.recovery.candidatePaths shouldBe paths
    direction.target shouldBe target
    try {
      reporter.Given("the actual retained native direction reconciliation and issued Review, with entry bytes mtimes index history and checks observed now")
      reporter.When("only the current host appends one unrelated safe path and ordinary Advance reopens retained normalization history")
      _declare_(extendedpaths)
      val extended = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      val extendedstate = _current_wait_state(fixture, target)
      reporter.Then("the identical Review and complete retained selection return without File index history or check replay")
      extended shouldBe reviewed
      extendedstate shouldBe retained

      Vector(paths.tail :+ extendedpaths.last,
        extendedpaths.updated(0, paths(1)).updated(1, paths(0))).foreach { invalidpaths =>
        reporter.Given("the original complete declaration remains bound to the retained native target")
        reporter.When("the current host removes or reorders an original path and invokes the same normal Advance")
        _declare_(invalidpaths)
        val refused = _advance(fixture)
        val response = if (refused.exitCode == 0) Some(refused.business[V.GoalPhaseAdvanceResult]) else None
        val unchanged = _current_wait_state(fixture, target)
        reporter.Then("the altered declaration refuses and every retained native fact source byte mtime index and check count stays unchanged")
        response match {
          case Some(value) =>
            value.kind shouldBe "Wait"
            value.work shouldBe None
            value.issuedWorkContext shouldBe None
          case None => refused.exitCode should not be 0
        }
        unchanged shouldBe retained
      }
    } finally Files.write(config, originalconfig)
    reporter.When("the exact original host declaration is restored and ordinary Advance recollects the same Session")
    val restored = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    val restoredstate = _current_wait_state(fixture, target)
    reporter.Then("the original pending Review remains reachable with accepted work UNKNOWN history and successful checks intact")
    restored shouldBe reviewed
    restoredstate shouldBe retained
    val receipt = fixture.scenario.root.resolve(if (resumed) "current-wait-append-resume-qualification.json"
      else "current-wait-append-qualification.json")
    val result = Json.obj("specificationOnly" -> Json.True,
      "qualificationMode" -> Json.fromString(if (resumed) "retained-suffix-only" else "fresh-full-episode"),
      "availableEvidence" -> Json.fromString(if (resumed) "saved-public-DTOs-and-current-native-entry-facts"
        else "fresh-operation-episode-and-current-native-facts"),
      "sessionId" -> Json.fromString(reviewed.sessionId),
      "reviewContinuation" -> Json.fromString(reviewed.issuedWorkContext.get.continuationId),
      "normalizationDirection" -> Json.fromString(direction.reference.identity),
      "unknownIntent" -> Json.fromString(target.recovery.intentIdentity),
      "retainedCheckCount" -> Json.fromInt(executions(fixture.scenario).size),
      "appendedDeclarationReplay" -> Json.fromString("retained"),
      "removedAndReorderedDeclarations" -> Json.fromString("refused"))
    val encoded = oldexit.fold(result)(value => result.mapObject(_.add("oldInvocationExitCode", Json.fromInt(value))))
    Files.writeString(receipt, encoded.noSpaces, java.nio.file.StandardOpenOption.CREATE_NEW)
    receipt
  }

  private def _resume_current_wait_append(reporter: org.scalatest.GivenWhenThen, locator: Path): Path = {
    import org.goldenport.convert.ValueReader
    import org.goldenport.record.io.RecordDecoder
    require(locator.isAbsolute && locator.normalize == locator && Files.isRegularFile(locator),
      "Select the explicit existing current-Wait resume input")
    val input = parse(Files.readString(locator)).toOption.get
    require(input.asObject.exists(_.keys.toSet == Set("specificationOnly", "root", "task", "description", "previousWork", "review")) &&
      input.hcursor.get[Boolean]("specificationOnly").contains(true), "Select only the actual specification-owned resume references")
    val root = Path.of(input.hcursor.get[String]("root").toOption.get)
    val task = Path.of(input.hcursor.get[String]("task").toOption.get)
    require(task == root.resolve("target/sm-workflow/tasks/source-history-document-task"), "Resume the exact retained task")
    def _artifact_(name: String): Path = {
      val path = Path.of(input.hcursor.get[String](name).toOption.get)
      require(path.isAbsolute && path.normalize == path && path.getParent == root.resolve("logs") &&
        Files.isRegularFile(path) && path.toRealPath() == path, "Select actual retained public stdout inside this fixture")
      path
    }
    def _read_[A: ValueReader](path: Path): A = take(summon[ValueReader[A]].readC(
      take(new RecordDecoder().json(Files.readString(path)))))
    reporter.Given("the explicit existing physical H1 Session and its actual saved describe previous-Implementation and successful Review DTOs")
    val description = _read_[V.GoalPhaseSourceHistoryNormalizationDescriptionResult](_artifact_("description"))
    val previous = _read_[V.GoalPhaseAdvanceResult](_artifact_("previousWork"))
    val reviewed = _read_[V.GoalPhaseAdvanceResult](_artifact_("review"))
    val target = take(N.targetC(description.target))
    val scenario = F.reopenSourceHistoryPrefix(root, documentInputsOwned = false)
    val fixture = Implemented(scenario, task, previous, Path.of(scenario.host.target.root).resolve(sourcePath))
    configure(scenario, enabledSettings)
    description.outcome shouldBe "adoption-required"
    description.sessionId shouldBe scenario.retrieve(task)
    previous.sessionId shouldBe description.sessionId
    reviewed.sessionId shouldBe previous.sessionId
    previous.work.get.kind shouldBe "ImplementStep"
    previous.work.get.stepIdentity shouldBe Some("step-2")
    reviewed.work.get.kind shouldBe "ReviewStep"
    reviewed.work.get.stepIdentity shouldBe Some("step-2")
    reviewed.validation.get.outcome shouldBe Some("passed")
    reviewed.work.get.selectedPlan shouldBe previous.work.get.selectedPlan
    reviewed.work.get.originalBase shouldBe previous.work.get.originalBase
    reviewed.work.get.completed shouldBe previous.work.get.completed
    previous.work.get.completed.map(_.step) shouldBe Vector("step-1")
    previous.repairProgress should not be None
    reviewed.repairProgress shouldBe previous.repairProgress
    target.pendingQuestion shouldBe None
    target.recovery.sessionId shouldBe reviewed.sessionId
    target.recovery.step shouldBe "step-2"
    target.recovery.planIdentity shouldBe previous.work.get.selectedPlan.identity
    target.recovery.planRevision shouldBe previous.work.get.selectedPlan.revision
    target.recovery.base shouldBe GoalPhaseOrderedProgression.Snapshot(previous.work.get.originalBase.head,
      previous.work.get.originalBase.tree)
    target.recovery.controllerTip shouldBe GoalPhaseOrderedProgression.Snapshot(previous.work.get.completed.last.commit.identity,
      previous.work.get.completed.last.commit.tree)
    target.recovery.completedPrefix.map(_.index) shouldBe Vector(0, 1, 2, 3)
    target.recovery.candidatePaths shouldBe scenario.host.goal.candidatePaths
    val entry = _current_wait_state(fixture, target)
    reporter.When("a read-only normal Advance recollects the already successful Review and actual native owners supply its retained direction prefix and UNKNOWN")
    val current = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    val unchanged = _current_wait_state(fixture, target)
    val direction = _receipt(fixture, N.reference(target), N.codec).payload.value
    val reconciled = observation(fixture, target)
    val unknown = _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity,
      target.recovery.intentRevision), GoalPhaseNativeSourceHistory.codec)
    val prefix = target.recovery.completedPrefix.map(pair => (
      _receipt(fixture, WorkflowMechanicalTransition.Reference(pair.intentIdentity, pair.intentRevision), GoalPhaseNativeSourceHistory.codec),
      _receipt(fixture, WorkflowMechanicalTransition.Reference(pair.resultIdentity, pair.resultRevision), GoalPhaseNativeSourceHistory.codec)))
    val oldresult = target.recovery.sessionId + ":ordered-candidate:" + target.recovery.sourceRevision +
      ":source-history-result-" + target.recovery.intentIndex
    val oldresultpresent = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw,
      catalog, GoalPhaseNativeSourceHistory.codec).toOption).exists(_.reference.identity == oldresult)
    reporter.Then("only available saved/current facts are asserted; no earlier byte snapshots invocation metadata or completed effects are reconstructed")
    current shouldBe reviewed
    unchanged shouldBe entry
    current.repairProgress shouldBe previous.repairProgress
    direction.target shouldBe target
    direction.priorExecutionEnded shouldBe true
    reconciled.outcome shouldBe Some("updated-current")
    unknown.payload.value shouldBe target.recovery.intent
    unknown.payload.value.outcome shouldBe None
    oldresultpresent shouldBe false
    prefix.zip(target.recovery.completedPrefix).foreach { case ((intent, result), pair) =>
      intent.payload.value shouldBe pair.intent
      result.payload.value shouldBe pair.result
    }
    _current_wait_append_suffix(reporter, fixture, target, reviewed, resumed = true, oldexit = None)
  }

  private def interrupt(fixture: Implemented, point: String): Unit = {
    val root = fixture.scenario.root
    val config = root.resolve(".textus/config.json")
    val assembly = root.resolve(".textus/assembly.yaml")
    val originalconfig = Files.readAllBytes(config)
    val originalassembly = Files.readAllBytes(assembly)
    val parameters = parse(Files.readString(config)).toOption.get.asObject.get.toVector.map { (key, value) =>
      key -> value.asString.getOrElse(value.noSpaces)
    }.toMap
    var acknowledged = Vector.empty[String]
    val callback: String => Consequence[Unit] = stage => {
      acknowledged = acknowledged :+ stage
      if (stage == point) Consequence.stateConflict("Test-owned normalization acknowledgement interrupted at " + point)
      else Consequence.unit
    }
    try _with_runtime_(root, "Goal", Some(fixture.scenario.host), hostParameters = parameters,
      goalAcknowledgement = Some(callback), componentInstance = "default") { (owner, component, actor, _) =>
      val result = owner.executeOperationResponseInContext(Request.of(component = component.name,
        service = "GoalPhaseService", operation = "advanceGoalPhase",
        properties = NativeWorkflowOperationInputFixture.advance(fixture.request.sessionId)), actor)
      result.isSuccess shouldBe false
      acknowledged should contain(point)
    } finally {
      Files.write(config, originalconfig)
      Files.write(assembly, originalassembly)
    }
  }

  "Public same-Session source-history normalization" should {
    "adopt a legacy pending Human with its successful prefix and reach actual selected SBT and independent Review through separate Advance" in {
      Given("SHR-04,SHR-05: accepted second-Step work, a completed first Step, four successful updates and real unknown intent; an optional explicit old image owns its legacy question")
      val episode = pending("source-normalization-public", legacyImage = true)
      val fixture = episode.fixture
      val repository = Path.of(fixture.scenario.host.target.root)
      val before = _current(fixture)
      val facts = _completed_facts(fixture)
      val index = indexBytes(fixture.scenario)
      val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)), Files.getLastModifiedTime(repository.resolve(path))))
      When("the new public storage-only description captures the exact unanswered question")
      val described = describe(fixture)
      val target = take(N.targetC(described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      Then("the source target and pending native revision remain bound without a new Human answer")
      target.pendingQuestion.get.pendingRevision shouldBe before.revision.value.toString
      target.recovery shouldBe take(GoalPhaseSourceHistoryRecovery.targetC(episode.question.human.get.sourceHistoryRecovery.get))
      _current(fixture) shouldBe before
      val unknown = _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity, "1"), GoalPhaseNativeSourceHistory.codec)
      val originalbytes = Files.readAllBytes(fixture.source).toVector

      Given("independent test host provenance for the already held direction and the actual ended old invocation")
      val input = direction(fixture, described)
      When("adoption supersedes the pending question without executing normalization or Advance")
      val adoptionoutcome = adopt(fixture, input).business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult].outcome
      Then("the held normalization direction is adopted")
      adoptionoutcome shouldBe "adopted"
      val adopted = _current(fixture)
      Then("only a distinct supersession is appended and all original evidence and counts are retained")
      adopted.history.dropRight(1) shouldBe before.history
      adopted.causation.value shouldBe N.cause(target)
      adopted.causation.value should include("superseded-by-normalization")
      adopted.suspension shouldBe None
      Files.readAllBytes(fixture.source).toVector shouldBe originalbytes
      indexBytes(fixture.scenario) shouldBe index
      _completed_facts(fixture) shouldBe facts
      executions(fixture.scenario).size shouldBe 1
      adopt(fixture, input).exitCode should not be 0

      When("the fixture corrects the malformed comment and a separate ordinary Advance reconciles it")
      corrected(fixture, target, normalized = false)
      val reviewcall = _advance(fixture)
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      Then("actual bounded update precedes fresh candidate and a real selected SBT check followed by independent ReviewStep")
      observation(fixture, target).outcome shouldBe Some("updated-current")
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.stepIdentity shouldBe Some("step-2")
      review.work.get.originalBase shouldBe episode.episode.completed.work.get.originalBase
      review.work.get.selectedPlan shouldBe episode.episode.completed.work.get.selectedPlan
      review.work.get.completed shouldBe episode.episode.completed.work.get.completed
      review.repairProgress shouldBe episode.question.repairProgress
      executions(fixture.scenario).size shouldBe 2
      Files.readString(fixture.source) should include("// Preserve this independent source comment.")
      Files.readString(fixture.source) should endWith(_later_corrected_text)
      prefix.foreach { (path, bytes, modified) =>
        fileBytes(repository.resolve(path)) shouldBe bytes
        Files.getLastModifiedTime(repository.resolve(path)) shouldBe modified
      }
      _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity, "1"), GoalPhaseNativeSourceHistory.codec) shouldBe unknown
      unknown.payload.value.outcome shouldBe None
      oldResultAbsent(fixture, target)
      _stored_json(fixture) should contain allElementsOf facts

      When("the same Session reopens and ordinary Advance is repeated")
      val complete = _current(fixture)
      val modified = Files.getLastModifiedTime(fixture.source)
      val reopened = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("repeated Advance returns the same independent Review")
      reopened shouldBe review
      Then("the same independent Review remains pending with no duplicate effect, check or question")
      _current(fixture) shouldBe complete
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      executions(fixture.scenario).size shouldBe 2
      complete.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
      oldResultAbsent(fixture, target)

      Given("P611-HISTORY-APPEND-01: an accepted explicit normalization direction and retained independent Review under the original ordered host inventory")
      val config = parse(Files.readString(fixture.scenario.root.resolve(".textus/config.json"))).toOption.get
      val key = "sm-workflow.runtime.definition"
      val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(config.hcursor.get[String](key).toOption.get).toOption.get))
      val candidatepaths = definition.goal.candidatePaths
      val extendedpaths = candidatepaths :+ "src/main/scala/RetainedNormalizationUnused.scala"
      val payloads = _stored_json(fixture).sorted
      val bytes = fileBytes(fixture.source)
      val staged = indexBytes(fixture.scenario)
      val checks = executions(fixture.scenario)
      val directions = _normalization_facts(fixture)
      val observed = observation(fixture, target)
      target.recovery.candidatePaths shouldBe candidatepaths
      When("the host appends one unrelated valid candidate path and ordinary Advance reopens saved normalization history")
      configure(fixture.scenario, enabledSettings.updated(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = extendedpaths))).noSpaces)))
      val extended = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the original complete direction target and UNKNOWN reconstruct unchanged without another effect or check")
      extended shouldBe review
      _current(fixture) shouldBe complete
      _stored_json(fixture).sorted shouldBe payloads
      _normalization_facts(fixture) shouldBe directions
      observation(fixture, target) shouldBe observed
      _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity, "1"), GoalPhaseNativeSourceHistory.codec) shouldBe unknown
      oldResultAbsent(fixture, target)
      fileBytes(fixture.source) shouldBe bytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe staged
      executions(fixture.scenario) shouldBe checks
      prefix.foreach { (path, originalbytes, time) =>
        fileBytes(repository.resolve(path)) shouldBe originalbytes
        Files.getLastModifiedTime(repository.resolve(path)) shouldBe time
      }

      Vector(candidatepaths.tail :+ extendedpaths.last,
        extendedpaths.updated(0, candidatepaths(1)).updated(1, candidatepaths(0))).foreach { invalidpaths =>
        Given("the saved explicit direction still binds every original ordered inventory entry")
        When("the current host removes or reorders an original prefix entry")
        configure(fixture.scenario, enabledSettings.updated(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
          definition.copy(goal = definition.goal.copy(candidatePaths = invalidpaths))).noSpaces)))
        val refused = _advance(fixture)
        Then("replay refuses without changing native history source index or completed checks")
        if (refused.exitCode == 0) refused.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        _current(fixture) shouldBe complete
        _stored_json(fixture).sorted shouldBe payloads
        fileBytes(fixture.source) shouldBe bytes
        Files.getLastModifiedTime(fixture.source) shouldBe modified
        indexBytes(fixture.scenario) shouldBe staged
        executions(fixture.scenario) shouldBe checks
      }
    }

    "observe an already satisfied current postcondition without writing or manufacturing the original result" in {
      Given("SHR-03: a real missing original result and an adopted existing policy")
      val episode = pending("source-normalization-observed")
      val fixture = episode.fixture
      val described = describe(fixture)
      val target = take(N.targetC(described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      adopt(fixture, direction(fixture, described)).exitCode shouldBe 0
      corrected(fixture, target, normalized = true)
      val bytes = fileBytes(fixture.source)
      val modified = Files.getLastModifiedTime(fixture.source)
      When("ordinary Advance reads the exact original-date current postcondition")
      val advancedkind = _advance(fixture).business[V.GoalPhaseAdvanceResult].work.get.kind
      Then("Advance reaches independent ReviewStep")
      advancedkind shouldBe "ReviewStep"
      Then("only an observed-current-postcondition is retained and File mtime and old UNKNOWN stay exact")
      observation(fixture, target).outcome shouldBe Some("observed-current-postcondition")
      fileBytes(fixture.source) shouldBe bytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      oldResultAbsent(fixture, target)
      executions(fixture.scenario).size shouldBe 2
    }

    "retain a truthful ambiguity failure and continue after ordinary correction through the same policy and separate Advance" in {
      Given("SHR-03,SHR-04: adopted policy while the affected managed header remains ambiguous")
      val episode = pending("source-normalization-known-failure")
      val fixture = episode.fixture
      val described = describe(fixture)
      val target = take(N.targetC(described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      val policy = direction(fixture, described)
      adopt(fixture, policy).exitCode shouldBe 0
      val bytes = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      val repository = Path.of(fixture.scenario.host.target.root)
      val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)), Files.getLastModifiedTime(repository.resolve(path))))
      val facts = _completed_facts(fixture)
      When("ordinary reconciliation encounters the known pure prewrite failure")
      val failed = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("its concrete failed observation is separate from the old missing result and grants no automatic retry")
      failed.kind shouldBe "Wait"
      val actual = observation(fixture, target)
      actual.outcome shouldBe Some("failed")
      actual.diagnostic.get should include("duplicate since or latest version")
      val stopped = _current(fixture)
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _current(fixture) shouldBe stopped
      fileBytes(fixture.source) shouldBe bytes
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario).size shouldBe 1
      oldResultAbsent(fixture, target)
      val failedfacts = _normalization_facts(fixture)

      Given("the same authorized source correction and the ended actual failed invocation")
      corrected(fixture, target, normalized = false)
      val correctedbytes = fileBytes(fixture.source)
      When("the same public owner describes the actual latest failed frontier")
      val resumedescription = describe(fixture)
      val resumed = take(N.targetC(resumedescription.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      Then("failed result provenance and original authority are retained without claiming the old Human is pending")
      resumed.recovery shouldBe target.recovery
      resumed.frontierRevision shouldBe stopped.revision.value.toString
      resumed.frontierCause shouldBe stopped.causation.value
      resumed.pendingQuestion shouldBe None
      resumed.resumeFrom.get.directionIdentity shouldBe N.reference(target).identity
      resumed.resumeFrom.get.resultIdentity shouldBe Some(N.reference(target).identity + ":reconciliation-result")
      resumed.resumeFrom.get.outcome shouldBe Some("failed")
      _current(fixture) shouldBe stopped

      When("stale target evidence false terminal assertions and a different authority reference are supplied")
      val stale = adopt(fixture, policy)
      val absent = adopt(fixture, direction(fixture, resumedescription, provision = false, held = Some(policy.reference)))
      val notended = adopt(fixture, direction(fixture, resumedescription, ended = false, provision = false, held = Some(policy.reference)))
      val crossedauthority = adopt(fixture, direction(fixture, resumedescription))
      Then("each refuses without native or File effects")
      Vector(stale, absent, notended, crossedauthority).foreach(_.exitCode should not be 0)
      _current(fixture) shouldBe stopped
      fileBytes(fixture.source) shouldBe correctedbytes
      indexBytes(fixture.scenario) shouldBe index

      When("fresh test-host lifecycle evidence binds that exact invocation under the same held policy")
      val input = direction(fixture, resumedescription, held = Some(policy.reference))
      val adopted = adopt(fixture, input).business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult]
      val next = _current(fixture)
      Then("only an adjacent distinct direction is appended and adoption performs no source update or selected check")
      adopted.outcome shouldBe "adopted"
      next.history.dropRight(1) shouldBe stopped.history
      next.causation.value shouldBe N.cause(resumed)
      N.reference(resumed) should not be N.reference(target)
      fileBytes(fixture.source) shouldBe correctedbytes
      executions(fixture.scenario).size shouldBe 1

      When("separate ordinary Advance reconciles the corrected file and captures the next candidate")
      val review = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the real selected SBT check reaches independent Review with unchanged Plan base counts prefix and old evidence")
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.selectedPlan shouldBe episode.episode.completed.work.get.selectedPlan
      review.work.get.originalBase shouldBe episode.episode.completed.work.get.originalBase
      review.work.get.completed shouldBe episode.episode.completed.work.get.completed
      review.repairProgress shouldBe episode.question.repairProgress
      observation(fixture, resumed).outcome shouldBe Some("updated-current")
      observation(fixture, target) shouldBe actual
      _normalization_facts(fixture) should contain allElementsOf failedfacts
      _completed_facts(fixture) should contain allElementsOf facts
      prefix.foreach { (path, bytes, modified) =>
        fileBytes(repository.resolve(path)) shouldBe bytes
        Files.getLastModifiedTime(repository.resolve(path)) shouldBe modified
      }
      oldResultAbsent(fixture, target)
      executions(fixture.scenario).size shouldBe 2

      When("the Session reopens and Advance repeats after successful continuation")
      val complete = _current(fixture)
      val modified = Files.getLastModifiedTime(fixture.source)
      val repeated = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the same Review and result are reused with no further write check or Human")
      repeated shouldBe review
      _current(fixture) shouldBe complete
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      executions(fixture.scenario).size shouldBe 2
      complete.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
      describe(fixture).exitCode should not be 0
    }

    "deny absent dedicated evidence and prior-ended false while honoring explicit existing Human Defer" in {
      Given("the legacy unanswered question and storage-only description")
      val episode = pending("source-normalization-denied")
      val fixture = episode.fixture
      val described = describe(fixture)
      val before = _current(fixture)
      When("normalization is requested without independent policy evidence or actual old execution termination")
      val absentevidence = adopt(fixture, direction(fixture, described, provision = false)).exitCode
      Then("adoption without dedicated evidence refuses")
      absentevidence should not be 0
      When("normalization also asserts that the old invocation has not ended")
      val priornotended = adopt(fixture, direction(fixture, described, ended = false, provision = false)).exitCode
      Then("adoption with a false prior-ended assertion refuses")
      priornotended should not be 0
      Then("neither assertion can change the question or original intent")
      _current(fixture) shouldBe before
      When("the existing ordinary Human is genuinely completed as Defer in this isolated test")
      val issued = _advance(fixture)
      val answer = _human_answer(fixture, issued.invocation.stdout, "Defer", "explicit-test-owned-old-defer")
      val deferaccepted = _human_submit(fixture, episode.question, answer).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted
      Then("the actual existing Human Defer submission is accepted")
      deferaccepted shouldBe true
      val deferred = _current(fixture)
      Then("public adoption refuses the completed deferral and repeated Advance remains stopped")
      describe(fixture).exitCode should not be 0
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _current(fixture) shouldBe deferred
      executions(fixture.scenario).size shouldBe 1
    }

    "leave normalization Defer storage-only and preserve the original unanswered question" in {
      Given("an exact legacy source-history target and independently supplied Defer direction")
      val episode = pending("source-normalization-policy-defer")
      val fixture = episode.fixture
      val described = describe(fixture)
      val before = _current(fixture)
      When("the public policy adoption receives Defer with no prior-ended assertion")
      val deferoutcome = adopt(fixture, direction(fixture, described, choice = "Defer", ended = false))
        .business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult].outcome
      Then("the policy adoption reports the deferred outcome")
      deferoutcome shouldBe "deferred"
      Then("nothing is appended and the same unanswered old question remains pending")
      _current(fixture) shouldBe before
      _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe episode.question
      _current(fixture) shouldBe before
    }

    "describe and adopt from saved completed-Step history without Git grants and refuse a changed captured full rule" in {
      Given("the exact saved legacy question with a full captured rule and a genuinely completed first Step")
      val episode = pending("source-normalization-storage-only")
      val fixture = episode.fixture
      val config = fixture.scenario.root.resolve(".textus/config.json")
      val original = parse(Files.readString(config)).toOption.get
      val key = "textus.subsystem.process-execution.plan"
      val plan = parse(original.hcursor.get[String](key).toOption.get).toOption.get
      val grants = plan.hcursor.downField("grants").focus.get.asArray.get.map { grant =>
        val capabilities = grant.hcursor.downField("capabilities").focus.get.asArray.get
          .filterNot(_.asString.exists(_.startsWith("sm-workflow-git-")))
        grant.mapObject(_.add("capabilities", Json.fromValues(capabilities)))
      }
      Files.writeString(config, original.mapObject(_.add(key,
        Json.fromString(plan.mapObject(_.add("grants", Json.fromValues(grants))).noSpaces))).noSpaces)
      val before = _current(fixture)
      val source = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      val facts = _completed_facts(fixture)
      When("storage-only public description and independent adoption traverse that same completed prefix")
      val described = describe(fixture)
      val target = take(N.targetC(described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      val adoptionaccepted = adopt(fixture, direction(fixture, described)).business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult].accepted
      Then("the saved completed-Step prefix permits independent adoption")
      adoptionaccepted shouldBe true
      val adopted = _current(fixture)
      Then("one exact supersession is retained with no File Git or SBT effect")
      adopted.history.dropRight(1) shouldBe before.history
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      _completed_facts(fixture) shouldBe facts
      executions(fixture.scenario).size shouldBe 1

      Given("the same current host now selects a different complete rule under the old provider identity")
      Files.writeString(config, original.noSpaces)
      val changedRule = target.recovery.historyPolicy.mapObject(_.add("removeHistoryAtSince", Json.False))
      configure(fixture.scenario, enabledSettings.updated(ruleKey, changedRule))
      When("ordinary Advance attempts to replay the adopted exact captured direction")
      val refused = _advance(fixture)
      Then("changed full policy refuses before new effects or an invented result")
      refused.exitCode should not be 0
      _current(fixture) shouldBe adopted
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario).size shouldBe 1
      oldResultAbsent(fixture, target)
    }

    Vector("goal-source-history-normalization-prepared" -> "before File work",
      "goal-source-history-normalization-executed" -> "after the managed write before its result").foreach { (point, stage) =>
    s"resume consecutive interrupted reconciliations $stage only after exact fresh lifecycle evidence under the same policy" in {
      Given("SHR-03: an adopted held policy, corrected source and the existing normal-host acknowledgement hook")
      val episode = pending("source-normalization-interrupted-" + point)
      val fixture = episode.fixture
      val described = describe(fixture)
      val target = take(N.targetC(described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      val policy = direction(fixture, described)
      adopt(fixture, policy).exitCode shouldBe 0
      corrected(fixture, target, normalized = false)
      val index = indexBytes(fixture.scenario)
      val repository = Path.of(fixture.scenario.host.target.root)
      val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)), Files.getLastModifiedTime(repository.resolve(path))))
      val facts = _completed_facts(fixture)
      When("the actual invocation returns after the fixture-owned interruption " + stage)
      interrupt(fixture, point)
      val stopped = _current(fixture)
      val bytes = fileBytes(fixture.source)
      val modified = Files.getLastModifiedTime(fixture.source)
      val retained = _normalization_facts(fixture)
      Then("the new writer remains unresolved with no invented result or terminal assertion")
      stopped.causation.value shouldBe "ordered-source-history-normalization-reconciliation:intent"
      _reconciliation_result_absent(fixture, target)

      When("ordinary Advance repeats before fresh lifecycle evidence is supplied")
      val waiting = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      val repeated = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      val resumedescription = describe(fixture)
      val resumed = take(N.targetC(resumedescription.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      val absent = adopt(fixture, direction(fixture, resumedescription, provision = false, held = Some(policy.reference)))
      val notended = adopt(fixture, direction(fixture, resumedescription, ended = false, provision = false, held = Some(policy.reference)))
      Then("missing or false terminal information never permits a concurrent retry and the description names the latest unknown invocation")
      waiting.kind shouldBe "Wait"
      waiting.message should include("terminal lifecycle evidence")
      repeated.kind shouldBe "Wait"
      waiting.repairProgress shouldBe episode.question.repairProgress
      Vector(absent, notended).foreach(_.exitCode should not be 0)
      resumed.recovery shouldBe target.recovery
      resumed.frontierRevision shouldBe stopped.revision.value.toString
      resumed.pendingQuestion shouldBe None
      resumed.resumeFrom.get.directionIdentity shouldBe N.reference(target).identity
      resumed.resumeFrom.get.intentIdentity shouldBe N.reference(target).identity + ":reconciliation-intent"
      resumed.resumeFrom.get.resultIdentity shouldBe None
      resumed.resumeFrom.get.resultRevision shouldBe None
      resumed.resumeFrom.get.outcome shouldBe None
      _current(fixture) shouldBe stopped
      fileBytes(fixture.source) shouldBe bytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario).size shouldBe 1
      oldResultAbsent(fixture, target)

      Given("the interruption helper has returned, ending that actual test-owned invocation")
      When("refreshed independent host evidence is prepared for its exact target and the same held policy direction")
      val input = direction(fixture, resumedescription, held = Some(policy.reference))
      val adopted = adopt(fixture, input).business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult]
      val next = _current(fixture)
      Then("adoption appends only one adjacent direction and preserves source bytes mtime counters and unresolved facts")
      adopted.outcome shouldBe "adopted"
      next.history.dropRight(1) shouldBe stopped.history
      next.causation.value shouldBe N.cause(resumed)
      fileBytes(fixture.source) shouldBe bytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      executions(fixture.scenario).size shouldBe 1
      _normalization_facts(fixture) should contain allElementsOf retained

      When("separate Advance creates another actual attempt and ends before File work through the same interruption hook")
      interrupt(fixture, "goal-source-history-normalization-prepared")
      val interruptedagain = _current(fixture)
      val retainedagain = _normalization_facts(fixture)
      val latestdescription = describe(fixture)
      val latest = take(N.targetC(latestdescription.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      val stale = adopt(fixture, input)
      Then("another genuine unresolved attempt binds its immediate predecessor rather than the initial direction")
      latest.resumeFrom.get.directionIdentity shouldBe N.reference(resumed).identity
      latest.resumeFrom.get.intentIdentity shouldBe N.reference(resumed).identity + ":reconciliation-intent"
      latest.resumeFrom.get.outcome shouldBe None
      latest.frontierRevision shouldBe interruptedagain.revision.value.toString
      latest.recovery shouldBe target.recovery
      N.reference(latest) should not be N.reference(resumed)
      stale.exitCode should not be 0
      _current(fixture) shouldBe interruptedagain
      fileBytes(fixture.source) shouldBe bytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      _reconciliation_result_absent(fixture, target)
      _reconciliation_result_absent(fixture, resumed)

      Given("the second interrupted invocation has also actually returned to its test host")
      When("the same policy receives fresh lifecycle information for that latest exact target and separate Advance runs")
      val latestinput = direction(fixture, latestdescription, held = Some(policy.reference))
      val latestadoption = adopt(fixture, latestinput).business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult]
      val beforeadvance = fileBytes(fixture.source)
      val beforemtime = Files.getLastModifiedTime(fixture.source)
      val review = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("current postcondition observation avoids repeated replacement or the still-unnormalized source receives one bounded update")
      latestadoption.outcome shouldBe "adopted"
      beforeadvance shouldBe bytes
      beforemtime shouldBe modified
      observation(fixture, latest).outcome shouldBe Some(if (point.endsWith("executed")) "observed-current-postcondition" else "updated-current")
      if (point.endsWith("executed")) {
        fileBytes(fixture.source) shouldBe bytes
        Files.getLastModifiedTime(fixture.source) shouldBe modified
      }
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.selectedPlan shouldBe episode.episode.completed.work.get.selectedPlan
      review.work.get.originalBase shouldBe episode.episode.completed.work.get.originalBase
      review.work.get.completed shouldBe episode.episode.completed.work.get.completed
      review.repairProgress shouldBe episode.question.repairProgress
      executions(fixture.scenario).size shouldBe 2
      _normalization_facts(fixture) should contain allElementsOf retainedagain
      _completed_facts(fixture) should contain allElementsOf facts
      prefix.foreach { (path, bytes, modified) =>
        fileBytes(repository.resolve(path)) shouldBe bytes
        Files.getLastModifiedTime(repository.resolve(path)) shouldBe modified
      }
      _reconciliation_result_absent(fixture, target)
      _reconciliation_result_absent(fixture, resumed)
      oldResultAbsent(fixture, target)

      When("the same Session reopens and ordinary Advance repeats after success")
      val complete = _current(fixture)
      val completebytes = fileBytes(fixture.source)
      val completemtime = Files.getLastModifiedTime(fixture.source)
      val replayed = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the same result and independent Review are reused without any additional write selected check or Human")
      replayed shouldBe review
      _current(fixture) shouldBe complete
      fileBytes(fixture.source) shouldBe completebytes
      Files.getLastModifiedTime(fixture.source) shouldBe completemtime
      executions(fixture.scenario).size shouldBe 2
      complete.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
      adopt(fixture, latestinput).exitCode should not be 0
      describe(fixture).exitCode should not be 0
    }
    }

    "reuse an attributable reconciliation result after interrupted acknowledgement without repeating the completed update" in {
      Given("a real adopted normalization and corrected unambiguous comment")
      val episode = pending("source-normalization-result-interrupted")
      val fixture = episode.fixture
      val described = describe(fixture)
      val target = take(N.targetC(described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      adopt(fixture, direction(fixture, described)).exitCode shouldBe 0
      corrected(fixture, target, normalized = false)
      When("native result persistence completes before its acknowledgement is interrupted")
      interrupt(fixture, "goal-source-history-normalization-recorded")
      val result = observation(fixture, target)
      val bytes = fileBytes(fixture.source)
      val modified = Files.getLastModifiedTime(fixture.source)
      Then("the completed update result is retained before the next Advance")
      result.outcome shouldBe Some("updated-current")
      When("the next ordinary Advance consumes that exact result")
      val advancedkind = _advance(fixture).business[V.GoalPhaseAdvanceResult].work.get.kind
      Then("Advance reaches actual selected SBT and independent Review without repeating the update")
      advancedkind shouldBe "ReviewStep"
      observation(fixture, target) shouldBe result
      fileBytes(fixture.source) shouldBe bytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      executions(fixture.scenario).size shouldBe 2
      oldResultAbsent(fixture, target)
    }
  }
}
