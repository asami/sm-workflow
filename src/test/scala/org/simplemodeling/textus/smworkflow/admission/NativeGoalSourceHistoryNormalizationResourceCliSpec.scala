package org.simplemodeling.textus.smworkflow.admission

import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.LocalDate
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.record.RecordPresentable
import org.scalatest.DoNotDiscover
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseSourceHistoryNormalization as N
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Explicit installed qualification through the scenario's normal assembly and
  * launcher. Policy, termination and independent review facts are test-owned;
  * these isolated episodes establish no live consumer consent.
  * @since   Nov.  5, 2026
  * @version Nov.  5, 2026
  */
private[admission] abstract class NativeGoalSourceHistoryNormalizationResourceCliSupport extends NativeGoalSourceHistoryRecoveryCliSupport {
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))

  override protected def _call(fixture: Implemented, service: String, operation: String,
    arguments: Vector[String]): F.Result = sys.props.get("smworkflow.spec.installedCommand") match {
    case None => super._call(fixture, service, operation, arguments)
    case Some(command) => fixture.scenario.callInstalled(Path.of(command), service, operation, arguments,
      assemblyDescriptor = Some(fixture.scenario.root.resolve(".textus/assembly.yaml")))
  }

  private def _normalization_description(fixture: Implemented): F.Result =
    _goal(fixture, N.describeOperation, Vector("--sessionId", fixture.request.sessionId))

  private def _normalization_direction(fixture: Implemented, described: F.Result): V.GoalPhaseSourceHistoryNormalizationAdoptionInput = {
    val description = described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
    val directory = Files.createTempDirectory(fixture.scenario.root, "resource-policy-direction-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString + ":held-policy", "1")
    val answer = V.GoalPhaseSourceHistoryNormalizationAnswer(N.choices.head,
      "Specification-owned existing policy and completed old invocation; preserve the unanswered Human", true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    def _file_(name: String, value: RecordPresentable): String = {
      val path = directory.resolve(name + ".json")
      Files.writeString(path, _json(value), UTF_8)
      path.toUri.toASCIIString
    }
    val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "source-history-normalization",
      described.invocation.stdout.toUri.toASCIIString, _file_("answer", answer), _file_("reference", reference),
      _file_("execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
      "entity:smworkflow.cli.host_evidence", Some("participant"))
    _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation))))
    V.GoalPhaseSourceHistoryNormalizationAdoptionInput(description.sessionId, description.target, answer.choice,
      answer.rationale, reference, answer.priorExecutionEnded)
  }

  private def _old_result_absent(fixture: Implemented, target: N.Target): Unit = {
    val identity = target.recovery.sessionId + ":ordered-candidate:" + target.recovery.sourceRevision +
      ":source-history-result-" + target.recovery.intentIndex
    _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog,
      GoalPhaseNativeSourceHistory.codec).toOption).exists(_.reference.identity == identity) shouldBe false
  }

  protected def resource_episode(name: String, observed: Boolean, deferred: Boolean): Unit = {
    Given("a real two-Step episode with four successful source-history entries and an actual missing-result UNKNOWN")
    val episode = _document_episode(name, later = true, canonicalHost = deferred)
    val fixture = episode.fixture
    val issued = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    issued.kind shouldBe "Human"
    issued.human.get.choices shouldBe GoalPhaseSourceHistoryRecovery.choices
    val recovery = take(GoalPhaseSourceHistoryRecovery.targetC(issued.human.get.sourceHistoryRecovery.get))
    recovery.intentIndex shouldBe 4
    recovery.completedPrefix.size shouldBe 4
    val config = fixture.scenario.root.resolve(".textus/config.json")
    if (deferred) {
      Given("the normal deferred host used since initial Start retains the same Session and unanswered Human with identical standard policies")
      val processplan = NativeCommonDelegateTaskFixture.processPlan(fixture.scenario.root, focused = false)
      val target = fixture.scenario.host.target
      val repositories = processplan.hcursor.get[Vector[io.circe.Json]]("gitRepositories").toOption.get
      repositories.size shouldBe 1
      val repository = repositories.head
      repository.hcursor.get[String]("target") shouldBe Right("sm-workflow-repository")
      repository.hcursor.get[String]("localBranch") shouldBe Right(target.localBranch)
      repository.hcursor.get[String]("remote") shouldBe Right(target.remote)
      repository.hcursor.get[String]("remoteBranch") shouldBe Right(target.remoteBranch)
      repository.hcursor.get[String]("endpoint") shouldBe Right(target.endpoint)
      val workspaces = processplan.hcursor.get[Vector[io.circe.Json]]("workspaces").toOption.get
        .filter(_.hcursor.get[String]("identity").contains("sm-workflow-repository"))
      workspaces.size shouldBe 1
      workspaces.head.hcursor.get[String]("root") shouldBe Right(target.root)
      val original = parse(Files.readString(config)).toOption.get
      original.hcursor.downField("sm-workflow.runtime.definition").focus shouldBe None
      parse(original.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get) shouldBe Right(processplan)
    }
    val before = _current(fixture)
    val facts = _completed_facts(fixture)
    val sourcefacts = _stored_json(fixture).filter(raw =>
      WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, GoalPhaseNativeSourceHistory.codec).isSuccess)
    val repository = Path.of(fixture.scenario.host.target.root)
    val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)), Files.getLastModifiedTime(repository.resolve(path))))
    val index = indexBytes(fixture.scenario)
    val described = _normalization_description(fixture)
    val description = described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
    val target = take(N.targetC(description.target))
    target.recovery shouldBe recovery
    target.pendingQuestion.get.pendingRevision shouldBe before.revision.value.toString
    _current(fixture) shouldBe before
    val unknown = _receipt(fixture, WorkflowMechanicalTransition.Reference(recovery.intentIdentity, recovery.intentRevision),
      GoalPhaseNativeSourceHistory.codec)
    unknown.payload.value.outcome shouldBe None

    Given("independent test-owned policy and actual-ended evidence prepared through the normal public service")
    val input = _normalization_direction(fixture, described)
    val body = "// Preserve this independent source comment.\n" + _later_corrected_text
    val normalized = take(WorkflowSourceHistoryRule.applyC(take(WorkflowSourceHistoryRule.decodeC(recovery.historyPolicy)),
      WorkflowSourceHistoryRule.Input(sourcePath, body, LocalDate.parse(recovery.intent.updatedOn), None, None,
        recovery.intent.dateUncertain))).text
    Files.writeString(fixture.source, if (observed) normalized else body, UTF_8)
    val sourcebytes = fileBytes(fixture.source)
    val modified = Files.getLastModifiedTime(fixture.source)
    val request = fixture.scenario.root.resolve("complete-normalization-adoption.json")
    // Legal JSON whitespace enlarges representation without inventing authority or changing the complete target.
    Files.writeString(request, _json(input) + (" " * (501 * 1024)), UTF_8)
    Files.size(request) should be > (500L * 1024)
    val serialized = parse(Files.readString(request)).toOption.get
    take(N.targetC(input.target)) shouldBe target
    val arguments = Vector("--inputResource", request.toUri.toASCIIString)

    When("the existing adoption Operation acquires only the complete local Resource under its owning call")
    _goal(fixture, N.adoptionOperation, arguments).business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult].outcome shouldBe "adopted"
    Then("the unanswered Human is superseded once and adoption performs no source or candidate effect")
    val adopted = _current(fixture)
    adopted.history.dropRight(1) shouldBe before.history
    adopted.causation.value shouldBe N.cause(target)
    adopted.causation.value should include("superseded-by-normalization")
    adopted.suspension shouldBe None
    fileBytes(fixture.source) shouldBe sourcebytes
    Files.getLastModifiedTime(fixture.source) shouldBe modified
    indexBytes(fixture.scenario) shouldBe index
    _completed_facts(fixture) shouldBe facts
    executions(fixture.scenario).size shouldBe 1
    _goal(fixture, N.adoptionOperation, arguments).exitCode should not be 0
    _current(fixture) shouldBe adopted
    parse(Files.readString(request)).toOption.get shouldBe serialized

    When("a separate ordinary Advance reconciles the original rule and date then runs normal candidate validation")
    val review = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    val observation = _receipt(fixture, WorkflowMechanicalTransition.Reference(N.reference(target).identity +
      ":reconciliation-result", "1"), N.observationCodec).payload.value
    Then("the actual branch reaches a fresh independent ReviewStep with unchanged accepted work and original UNKNOWN")
    observation.outcome shouldBe Some(if (observed) "observed-current-postcondition" else "updated-current")
    Files.readString(fixture.source) shouldBe normalized
    if (observed) {
      fileBytes(fixture.source) shouldBe sourcebytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
    }
    review.work.get.kind shouldBe "ReviewStep"
    review.work.get.stepIdentity shouldBe Some("step-2")
    review.work.get.selectedPlan shouldBe episode.completed.work.get.selectedPlan
    review.work.get.originalBase shouldBe episode.completed.work.get.originalBase
    review.work.get.completed shouldBe episode.completed.work.get.completed
    review.repairProgress shouldBe issued.repairProgress
    review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
    executions(fixture.scenario).size shouldBe 2
    prefix.foreach { (path, bytes, time) =>
      fileBytes(repository.resolve(path)) shouldBe bytes
      Files.getLastModifiedTime(repository.resolve(path)) shouldBe time
    }
    _receipt(fixture, WorkflowMechanicalTransition.Reference(recovery.intentIdentity, recovery.intentRevision),
      GoalPhaseNativeSourceHistory.codec) shouldBe unknown
    _old_result_absent(fixture, target)
    _stored_json(fixture) should contain allElementsOf sourcefacts
    _stored_json(fixture) should contain allElementsOf facts

    When("the same independent Review is recollected through ordinary Advance")
    val complete = _current(fixture)
    val finalmodified = Files.getLastModifiedTime(fixture.source)
    val recollected = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("no successful prefix source normalization or selected check is repeated")
    recollected shouldBe review
    _current(fixture) shouldBe complete
    Files.getLastModifiedTime(fixture.source) shouldBe finalmodified
    executions(fixture.scenario).size shouldBe 2
    complete.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
    _old_result_absent(fixture, target)
    if (deferred) parse(Files.readString(config)).toOption.get.hcursor.downField("sm-workflow.runtime.definition").focus shouldBe None
  }

}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryNormalizationResourceCliSpec extends NativeGoalSourceHistoryNormalizationResourceCliSupport {
  "Public normalization adoption through a complete Resource request" should {
    "normalize the affected source under its retained authority after configured-runtime Resource adoption" in {
      resource_episode("normalization-resource-update", observed = false, deferred = false)
    }
  }
}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryNormalizationResourceDeferredCliSpec extends NativeGoalSourceHistoryNormalizationResourceCliSupport {
  "Public normalization adoption through a complete Resource request" should {
    "observe the satisfied postcondition without replacement after standard deferred Resource session resolution" in {
      resource_episode("normalization-resource-observed-deferred", observed = true, deferred = true)
    }
  }
}
