package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransitionJsonV1, WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncPurpose as P
import RepositorySyncNativeProgressStore as N
import SplitPhaseExplicitPartitionFixture.take

/** Small isolated repositories and the real public CLI, normal host plan and
  * SQLite provider. These helpers observe native rows without writing them.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
private[admission] trait NativeRepositoryContinuationCliFixture extends RepositorySyncMaintenanceFixture {
  protected def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  protected def save(root: Path, name: String, value: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, value)
    path.toUri.toASCIIString
  }
  protected def configure(scenario: F.Scenario, parameters: Map[String, String]): Unit = {
    val path = scenario.root.resolve(".textus/config.json")
    val original = parse(Files.readString(path)).toOption.get
    Files.writeString(path, original.mapObject(fields => parameters.foldLeft(fields.remove("sm-workflow.runtime.definition")) {
      case (prior, (key, value)) => prior.add(key, Json.fromString(value))
    }).noSpaces)
  }
  protected def fresh(name: String, checked: Boolean = false): F.Scenario = {
    val scenario = F.fresh(name, bootstrapSelectedInput = false,
      sbtRepair = Option.when(checked)(TypedSbtHostPlanFixture.normalExecutable))
    configure(scenario, maintenance_parameters() ++ Map(
      "textus.subsystem.process-execution.plan" -> process_plan(scenario.root, "Equal", scenario.host.goal).noSpaces,
      "textus.resource.url.file.roots" -> scenario.root.toString))
    scenario
  }
  protected def start(scenario: F.Scenario): String = scenario.repositoryCall("startRepositorySync", Vector("--input",
    "{\"repositoryIdentity\":\"continuation-fixture\",\"syncMode\":\"FULL_STATE\"}")).business[V.RepositorySyncStartResult].sessionId
  protected def advance(scenario: F.Scenario, session: String): F.Result =
    scenario.repositoryCall("advanceRepositorySync", Vector("--sessionId", session))
  protected def describe(scenario: F.Scenario, session: String): F.Result =
    scenario.repositoryCall(RepositorySyncValidationContinuation.describeOperation, Vector("--sessionId", session))
  protected def direction(scenario: F.Scenario, session: String, described: F.Result, choice: String,
    ended: Boolean, accepted: Boolean): V.RepositorySyncValidationContinuationAdoptionInput = {
    val result = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
    val directory = Files.createTempDirectory(scenario.root, "fixture-owned-independent-direction-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val participant = take(RepositorySyncValidationContinuation.anyTargetC(result.target)) match {
      case RepositorySyncValidationContinuation.FormalFailureTarget(value) => value.context.participantIdentity
      case RepositorySyncValidationContinuation.PurposeContentTarget(value) => value.context.participantIdentity
      case _ => fail("This fixture accepts only its explicit formal-failure or purpose-content policy")
    }
    val answer = V.RepositorySyncValidationContinuationAnswer(choice,
      "Specification-owned independent direction after the prior CLI process returned; preserve original worker uncertainty", ended, accepted)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(participant), None, None)
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", "validation-continuation",
      described.invocation.stdout.toUri.toASCIIString, save(directory, "answer.json", json(answer)),
      save(directory, "reference.json", json(reference)), save(directory, "execution.json", json(execution)),
      "normal-development-host", "2026-11-03T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some(participant))
    val prepared = scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(input))))
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    scenario.selectPreparedImport(prepared)
    V.RepositorySyncValidationContinuationAdoptionInput(session, result.target, choice, answer.rationale,
      reference, ended, accepted, execution)
  }
  protected def adopt(scenario: F.Scenario, input: V.RepositorySyncValidationContinuationAdoptionInput): F.Result =
    scenario.repositoryCall(RepositorySyncValidationContinuation.adoptionOperation, Vector("--input", json(input)))
  protected def submit_purpose(scenario: F.Scenario, session: String, result: P.Result): F.Result = {
    val reference = V.WorkflowArtifactReference("fixture-purpose-result", "1")
    scenario.repositoryCall("submitRepositoryPurposeResult", Vector("--sessionId", session,
      "--result", result.asJson.noSpaces, "--resultReference", json(reference), "--executionEvidence",
      json(V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-purpose-author"), None, None))))
  }
  protected def stored(scenario: F.Scenario): Vector[String] = {
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + scenario.root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro",
      new java.util.Properties())
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:"))
              .foreach(raw => values += raw.stripPrefix("cncf.workflow-json.v1:"))
          } } finally rows.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
  protected def current(scenario: F.Scenario, session: String): InstanceRecord = {
    val records = stored(scenario).flatMap(WorkflowInstanceRecordJsonV1.decodeC(_, _catalog).toOption).filter(_.identity.value == session)
    records.size shouldBe 1
    records.head
  }
  protected def receipts(scenario: F.Scenario, session: String) = stored(scenario).flatMap(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, N.codec).toOption).filter(_.next.identity.value == session)
  protected def purpose_inputs(scenario: F.Scenario): Vector[P.Request] = stored(scenario).flatMap(raw =>
    WorkflowWorkOrderJsonV1.decodeC(raw, P.inputCodec).toOption).flatMap(_.current match {
      case order: WorkflowContinuation.WorkOrder[P.Request] => order.request.input.map(_.value)
      case _ => None
    })
  protected def episode_stage(kind: String): String = {
    val stage = sys.props.getOrElse("smworkflow.spec." + kind + "EpisodeStage", "normal")
    require(Set("normal", "before", "after")(stage), "Select normal, before or after episode stage")
    stage
  }
  private def _episode_path(kind: String, route: String): Path = {
    val directory = Path.of(sys.props.getOrElse("smworkflow.spec." + kind + "EpisodeDirectory",
      fail("Select an explicit absolute retained episode directory")))
    require(directory.isAbsolute && directory.normalize == directory && Files.isDirectory(directory),
      "Select an existing absolute episode directory")
    directory.resolve(route + ".json")
  }
  protected def retain_episode(kind: String, route: String, scenario: F.Scenario, session: String, stdout: Path): Unit = {
    require(sys.props.contains("smworkflow.spec.installedCommand"), "Before publication requires the actual old installed command")
    val metadata = Json.obj("root" -> scenario.root.toString.asJson, "session" -> session.asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
      "stdout" -> Files.readString(stdout).asJson)
    Files.writeString(_episode_path(kind, route), metadata.noSpaces, StandardOpenOption.CREATE_NEW)
  }
  protected def reopen_episode(kind: String, route: String): (F.Scenario, String, String) = {
    require(sys.props.contains("smworkflow.spec.installedCommand"), "After publication requires the actual new installed command")
    val metadata = parse(Files.readString(_episode_path(kind, route))).toOption.get
    val root = Path.of(metadata.hcursor.get[String]("root").toOption.get)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(metadata.hcursor.get[Json]("host").toOption.get))
    val plan = metadata.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get
    val logs = Files.list(root.resolve("logs"))
    val starting = try math.max(1000, logs.filter(path => path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) finally logs.close()
    (F.reopenInstalledRepository(root, host, plan, starting), metadata.hcursor.get[String]("session").toOption.get,
      metadata.hcursor.get[String]("stdout").toOption.get)
  }
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryPurposeContentContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositoryContinuationCliFixture {
  "RS-PURPOSE-03 isolated public CLI" should {
    "supersede exactly one unanswered 36-file issue and issue one fresh inventory after separate Advance" in {
      Given("a tiny real repository with 36 ordinary dirty documents and one genuine unanswered Purpose WorkOrder")
      val stage = episode_stage("purposeContent")
      val reopened = Option.when(stage == "after")(reopen_episode("purposeContent", "purpose"))
      val scenario = reopened.fold(fresh("purpose-content-public"))(_._1)
      val repository = Path.of(scenario.host.target.root)
      val paths = (1 to 36).toVector.map(n => f"docs/refresh-$n%02d.md")
      if (stage != "after") paths.foreach(path => Files.writeString(repository.resolve(path), "old ordinary document\n"))
      val session = reopened.fold(start(scenario))(_._2)
      val issued = if (stage == "after") None else {
        When("ordinary Advance observes the dirty documents")
        Some(advance(scenario, session))
      }
      issued.foreach { result =>
        Then("the runtime requests a Purpose result")
        result.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
      }
      val old = current(scenario, session)
      val original = purpose_inputs(scenario).head
      val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
      val tree = git(scenario.root, "-C", repository.toString, "write-tree")
      if (stage != "after") paths.take(3).foreach(path => Files.writeString(repository.resolve(path), "current complete document\n"))
      if (stage == "before") {
        When("the old installed runtime episode and its actual stdout are retained before publication")
        retain_episode("purposeContent", "purpose", scenario, session, issued.get.invocation.stdout)
        Then("the retained session still has its original unanswered Purpose inventory")
        old.suspension should not be empty
        purpose_inputs(scenario) shouldBe Vector(original)
      } else {
        When("public description observes the changed contents")
        val described = describe(scenario, session)
        val target = RepositorySyncPurposeContentContinuation.decodeTarget(parse(take(
          described.business[V.RepositorySyncValidationContinuationDescriptionResult].target.toJsonStringC)).toOption.get).fold(fail(_), identity)
        Then("the target binds the original 36-file inventory and exactly three content changes")
        target.original shouldBe original
        target.original.files.size shouldBe 36
        target.original.files.zip(target.current.files).count((before, after) => before.worktree != after.worktree) shouldBe 3
        When("prepared direction is submitted without the required ended-worker assertion")
        val input = direction(scenario, session, described, "AdoptCurrentPurposeContent", true, true)
        val malformed = input.copy(priorExecutionEnded = false)
        val refused = adopt(scenario, malformed)
        Then("the incomplete direction is refused")
        refused.exitCode should not be 0
        When("the complete prepared direction is adopted")
        val adoption = adopt(scenario, input)
        val admitted = current(scenario, session)
        Then("adoption only supersedes the old issue and retains Git and the old inventory unchanged")
        adoption.business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "adopted"
        admitted.currentProgression shouldBe Some(ProgressionReference("sync.PurposeContentContinuationAdmitted"))
        admitted.revision shouldBe old.revision.next
        admitted.suspension shouldBe None
        purpose_inputs(scenario) shouldBe Vector(original)
        git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
        git(scenario.root, "-C", repository.toString, "write-tree") shouldBe tree
        When("the same direction is submitted again")
        val repeated = adopt(scenario, input)
        Then("the adopted direction is reported without another transition")
        repeated.business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
        When("the old worker submits its superseded Purpose result")
        val stale = submit_purpose(scenario, session, grouping("stale original proposal"))
        Then("the stale result is refused")
        stale.exitCode should not be 0
        When("a separate ordinary Advance issues fresh native work and is called again")
        val first = advance(scenario, session)
        val refreshed = current(scenario, session)
        val count = receipts(scenario, session).count(_.payload.value.stage == "PurposeInventory")
        val second = advance(scenario, session)
        Then("one new immutable inventory and issue replace the old request without replaying effects")
        first.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
        second.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
        count shouldBe 2
        receipts(scenario, session).count(_.payload.value.stage == "PurposeInventory") shouldBe count
        purpose_inputs(scenario).toSet shouldBe Set(original, target.current)
        refreshed.suspension.get.continuationIdentity should not be old.suspension.get.continuationIdentity
        When("the old target is submitted after fresh work has been issued")
        val obsolete = adopt(scenario, input)
        Then("the obsolete target is refused")
        obsolete.exitCode should not be 0
      }
    }
    if (episode_stage("purposeContent") == "normal") {
      "refuse source text that changes again after admission without silently replacing the adopted target" in {
      Given("a native unanswered purpose issue and an independently adopted content refresh")
      val scenario = fresh("purpose-content-drift")
      val path = Path.of(scenario.host.target.root).resolve("docs/refresh.md")
      Files.writeString(path, "first\n")
      val session = start(scenario)
      advance(scenario, session).business[V.RepositorySyncAdvanceResult]
      Files.writeString(path, "second\n")
      val described = describe(scenario, session)
      adopt(scenario, direction(scenario, session, described, "AdoptCurrentPurposeContent", true, true))
        .business[V.RepositorySyncValidationContinuationAdoptionResult].accepted shouldBe true
      val admitted = current(scenario, session)
      When("source text changes for a third time before separate Advance")
      Files.writeString(path, "third\n")
      val result = advance(scenario, session)
      Then("the adopted target stays immutable and no fresh issue or inventory is created")
      result.exitCode should not be 0
      current(scenario, session) shouldBe admitted
      purpose_inputs(scenario).size shouldBe 1
      }
    }
  }
}
