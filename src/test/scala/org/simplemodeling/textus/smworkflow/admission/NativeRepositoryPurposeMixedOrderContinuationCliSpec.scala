package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncPurposeContentContinuation as C
import SplitPhaseExplicitPartitionFixture.take

/** A fresh tiny Git repository retains one unanswered native mixed-order issue
  * across installed-runtime publication. Directions belong only to this fixture;
  * they do not claim live Human or provider acceptance.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryPurposeMixedOrderContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositoryContinuationCliFixture {
  private val _kind = "purposeMixedOrder"
  private val _route = "mixed-purpose"
  private def _installed: Path = {
    val property = sys.props.get("smworkflow.spec.installedCommand")
    val executable = Path.of(property.orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select the actual absolute installed command")))
    executable.isAbsolute shouldBe true
    executable.normalize shouldBe executable
    Files.isRegularFile(executable) shouldBe true
    Files.isExecutable(executable) shouldBe true
    if (property.isEmpty) System.setProperty("smworkflow.spec.installedCommand", executable.toString)
    executable
  }
  private def _stage: String = {
    Vector("Stage", "Directory").foreach { suffix =>
      val key = "smworkflow.spec.purposeMixedOrderEpisode" + suffix
      if (!sys.props.contains(key)) sys.env.get("SM_WORKFLOW_SPEC_PURPOSE_MIXED_ORDER_EPISODE_" + suffix.toUpperCase)
        .foreach(value => System.setProperty(key, value))
    }
    require(sys.props.contains("smworkflow.spec.purposeMixedOrderEpisodeStage"), "Select an explicit mixed-order episode stage")
    val directory = Path.of(sys.props.getOrElse("smworkflow.spec.purposeMixedOrderEpisodeDirectory",
      fail("Select an explicit absolute mixed-order episode directory")))
    require(directory.isAbsolute && directory.normalize == directory && Files.isDirectory(directory),
      "Select an existing absolute mixed-order episode directory")
    episode_stage(_kind)
  }
  private def _fresh(): F.Scenario = {
    val scenario = fresh("purpose-mixed-order-public")
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    val original = Files.readString(descriptor, UTF_8)
    original should include("      id: participant\n")
    Files.writeString(descriptor, original.replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    scenario
  }
  private def _success(result: F.Result): F.Result = {
    withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
    result.invocation.argv.head shouldBe _installed.toString
    result
  }

  "ID04-PURPOSE-ORDER installed public CLI" should {
    "retain and refresh one native mixed-order inventory on the same Session across publication" in {
      Given("an explicitly selected installed command and a tiny real repository or its retained before episode")
      _installed
      val stage = _stage
      val reopened = Option.when(stage == "after")(reopen_episode(_kind, _route))
      val scenario = reopened.fold(_fresh())(_._1)
      val repository = Path.of(scenario.host.target.root)
      val tracked = (1 to 24).toVector.map(n => f"z-tracked/file-$n%02d.md")
      val untracked = (1 to 12).toVector.map(n => f"a-untracked/file-$n%02d.md")
      val paths = tracked ++ untracked
      if (stage != "after") {
        Files.createDirectories(repository.resolve("z-tracked"))
        Files.createDirectories(repository.resolve("a-untracked"))
        tracked.foreach(path => Files.writeString(repository.resolve(path), "baseline ordinary document\n"))
        git(scenario.root, "-C", repository.toString, "add", "--", "z-tracked")
        git(scenario.root, "-C", repository.toString, "commit", "--no-verify", "--no-gpg-sign", "-m", "Mixed-order fixture baseline")
        paths.foreach(path => Files.writeString(repository.resolve(path), "old ordinary document\n"))
      }
      val session = reopened.fold(start(scenario))(_._2)
      val issued = if (stage == "after") None else {
        When("ordinary Advance captures 24 tracked modifications followed by 12 untracked documents")
        Some(_success(advance(scenario, session)))
      }
      issued.foreach(_.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult"))
      val old = current(scenario, session)
      val original = purpose_inputs(scenario).head
      val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
      val index = git(scenario.root, "-C", repository.toString, "write-tree")
      val snapshotPath = scenario.root.resolve("purpose-mixed-order-original.json")
      val snapshot = Json.obj("record" -> take(WorkflowInstanceRecordJsonV1.encodeC(old)).asJson,
        "inventory" -> C.requestJson(original), "head" -> head.asJson, "index" -> index.asJson)
      Then("the original immutable inventory has exactly the native unsorted paths and statuses")
      original.files.map(_.path) shouldBe paths
      paths should not be paths.sorted
      original.files.take(24).map(file => (file.indexCode, file.worktreeCode)).distinct shouldBe Vector((" ", "M"))
      original.files.drop(24).map(file => (file.indexCode, file.worktreeCode)).distinct shouldBe Vector(("?", "?"))
      purpose_inputs(scenario) shouldBe Vector(original)
      old.suspension should not be empty
      if (stage == "after") {
        parse(Files.readString(snapshotPath)).toOption.get shouldBe snapshot
      } else {
        Files.writeString(snapshotPath, snapshot.noSpaces, StandardOpenOption.CREATE_NEW)
        tracked.take(3).foreach(path => Files.writeString(repository.resolve(path), "current complete document\n"))
      }

      if (stage == "before") {
        When("the old installed runtime describes the three changed worktree texts")
        val refused = describe(scenario, session)
        Then("the old lexical-order predicate refuses 409 without changing the record or inventory")
        refused.exitCode should not be 0
        refused.invocation.logs should include("409")
        current(scenario, session) shouldBe old
        purpose_inputs(scenario) shouldBe Vector(original)
        git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
        git(scenario.root, "-C", repository.toString, "write-tree") shouldBe index
        retain_episode(_kind, _route, scenario, session, refused.invocation.stdout)
      } else {
        When("the fixed installed runtime describes the same unanswered issue")
        val described = _success(describe(scenario, session))
        val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
        val target = take(RepositorySyncValidationContinuation.anyTargetC(description.target)) match {
          case RepositorySyncValidationContinuation.PurposeContentTarget(value) => value
          case _ => fail("The actual purpose content continuation target is required")
        }
        Then("the complete original metadata and native order survive with exactly three text changes")
        target.original shouldBe original
        target.current.files.map(_.path) shouldBe paths
        target.original.files.zip(target.current.files).count((before, after) => before.worktree != after.worktree) shouldBe 3
        target.original.files.zip(target.current.files).foreach { (before, after) =>
          before.copy(worktree = after.worktree) shouldBe after
        }
        target.original.copy(files = target.current.files) shouldBe target.current
        current(scenario, session) shouldBe old

        When("fixture-owned direction accepts the returned prior process and its UNKNOWN effects once")
        val input = direction(scenario, session, described, "AdoptCurrentPurposeContent", true, true)
        val adoption = _success(adopt(scenario, input))
        val admitted = current(scenario, session)
        Then("only supersession is admitted while original work and history remain immutable")
        adoption.business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "adopted"
        admitted.currentProgression shouldBe Some(ProgressionReference("sync.PurposeContentContinuationAdmitted"))
        admitted.revision shouldBe old.revision.next
        admitted.suspension shouldBe None
        admitted.history.take(old.history.size) shouldBe old.history
        purpose_inputs(scenario) shouldBe Vector(original)
        stored(scenario).flatMap(raw => parse(raw).toOption).exists(_.findAllByKey("originalWorkerEffects").contains(Json.fromString("UNKNOWN"))) shouldBe true

        When("the identical direction is adopted again")
        val repeated = _success(adopt(scenario, input))
        Then("the repeated adoption is idempotent")
        repeated.business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
        current(scenario, session) shouldBe admitted

        When("the old worker submits its result after supersession and before any fresh assignment")
        val stale = submit_purpose(scenario, session, grouping("stale original mixed-order proposal"))
        Then("the superseded result is refused without changing the admitted state or original inventory")
        stale.exitCode should not be 0
        stale.invocation.logs should include("result session has no pending assignment")
        current(scenario, session) shouldBe admitted
        purpose_inputs(scenario) shouldBe Vector(original)

        When("a separate Advance issues fresh purpose work and Advance is repeated")
        val first = _success(advance(scenario, session))
        val refreshed = current(scenario, session)
        val second = _success(advance(scenario, session))
        Then("exactly one fresh immutable inventory and a different WorkOrder retain the original history")
        first.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
        second.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
        receipts(scenario, session).count(_.payload.value.stage == "PurposeInventory") shouldBe 2
        purpose_inputs(scenario).size shouldBe 2
        purpose_inputs(scenario).toSet shouldBe Set(original, target.current)
        refreshed.suspension.get.continuationIdentity should not be old.suspension.get.continuationIdentity
        current(scenario, session) shouldBe refreshed
        refreshed.history.take(old.history.size) shouldBe old.history

        When("the old target is submitted after fresh work is issued")
        val obsolete = adopt(scenario, input)
        Then("the obsolete target is refused and Git HEAD and index remain unchanged")
        obsolete.exitCode should not be 0
        current(scenario, session) shouldBe refreshed
        purpose_inputs(scenario).size shouldBe 2
        git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
        git(scenario.root, "-C", repository.toString, "write-tree") shouldBe index
      }
    }
  }
}
