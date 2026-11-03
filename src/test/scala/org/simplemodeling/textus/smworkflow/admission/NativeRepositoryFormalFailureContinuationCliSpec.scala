package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncNativeMergeValidation as C
import RepositorySyncPurpose as P
import RepositorySyncFormalFailureContinuation as N
import SplitPhaseExplicitPartitionFixture.take

/** Genuine normal SBT processes in a tiny product repository. The failure is
  * the SBT launcher's actual nonzero exit under an isolated invalid JVM launch option;
  * neither a success provider nor an authored process receipt participates.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryFormalFailureContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositoryContinuationCliFixture {
  private val _checks = Vector(C.Check("formal-compile", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("formal-test", 2, WorkflowSbtValidation.Kind.Test), C.Check("formal-lint", 3, WorkflowSbtValidation.Kind.FixCheck))
  private def _array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
  private def _plan(scenario: NativeWorkflowCliFixture.Scenario, purpose: Boolean, broken: Boolean): Json = {
    val base = process_plan(scenario.root, "Equal", scenario.host.goal)
    val selections = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.FixCheck).zipWithIndex.map { (operation, index) =>
      TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(index + 1), operation = operation)
    }
    val programs = selections.zipWithIndex.flatMap { (selected, index) => _array(selected, "sbtPrograms").map { program =>
      val target = if (purpose) program.mapObject(_.add("target", "purpose-slot-1".asJson)) else program
      if (broken && index == 1) target.mapObject(fields => fields.add("environment", fields("environment").get.mapObject(
        _.add("JAVA_TOOL_OPTIONS", "-XX:SmWorkflowInvalidFixtureOption".asJson)))) else target
    }}
    val workspaces = if (purpose) _array(base, "workspaces") else
      (_array(base, "workspaces") ++ selections.flatMap(_array(_, "workspaces"))).distinct
    val grants = if (purpose) _array(base, "workspaceGrants") else
      (_array(base, "workspaceGrants") ++ selections.flatMap(_array(_, "workspaceGrants"))).distinct
    base.mapObject(fields => fields.add("sbtPrograms", programs.asJson).add("workspaces", workspaces.asJson)
      .add("workspaceGrants", grants.asJson).add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant =>
        grant.add("capabilities", grant("capabilities").get.mapArray(existing =>
          (existing ++ programs.map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
  }
  private def _parameters(purpose: Boolean): Map[String, String] = {
    val impact = RepositorySyncProgramImpact.Policy(_checks.map(check => RepositorySyncProgramImpact.Coverage(check,
      Vector("value.txt", "src", "build.sbt", "project"), Vector("value.txt", "src", "build.sbt", "project"),
      "Real product and build inputs require the fixed checks")), _checks,
      Vector("src", "build.sbt", "project"), "Selected build and product checks", Vector.empty)
    val slot = P.Slot("purpose-slot-1", _checks,
      GoalPhaseStepAssessment.Policy("formal-purpose", "code", "required", "off", "off", Vector.empty))
    maintenance_parameters() ++ Map("sm-workflow.repository-sync.program-inputs" -> impact.asJson.noSpaces,
      "sm-workflow.repository-sync.dirty-purposes" -> P.Policy(Vector(slot), Vector.empty).asJson.noSpaces)
  }
  private def _scenario(name: String, purpose: Boolean): NativeWorkflowCliFixture.Scenario = {
    val scenario = fresh(name, checked = true)
    val repository = Path.of(scenario.host.target.root)
    Files.writeString(repository.resolve("project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
    Files.copy(Path.of(".scalafix.conf").toAbsolutePath, repository.resolve(".scalafix.conf"))
    Files.writeString(repository.resolve("build.sbt"), "\naddCommandAlias(\"fixCheck\", \"Test / scalafix --check\")\n", StandardOpenOption.APPEND)
    git(scenario.root, "-C", repository.toString, "add", "--", "build.sbt", "project/plugins.sbt", ".scalafix.conf")
    git(scenario.root, "-C", repository.toString, "commit", "-m", "Retain genuine isolated build and lint inputs")
    git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    if (!purpose) {
      val peer = scenario.root.resolve("formal-peer")
      git(scenario.root, "clone", "--branch", "main", "--", scenario.host.target.endpoint, peer.toString)
      Files.writeString(peer.resolve("value.txt"), "expected\n")
      git(scenario.root, "-C", peer.toString, "add", "--", "value.txt")
      git(scenario.root, "-C", peer.toString, "commit", "-m", "Remote product input")
      git(scenario.root, "-C", peer.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
      Files.writeString(repository.resolve("docs/formal-local.md"), "independent local document\n")
      git(scenario.root, "-C", repository.toString, "add", "--", "docs/formal-local.md")
      git(scenario.root, "-C", repository.toString, "commit", "-m", "Local independent document")
    }
    configure(scenario, _parameters(purpose) + ("textus.subsystem.process-execution.plan" -> _plan(scenario, purpose, broken = true).noSpaces))
    scenario
  }
  private def _formal(scenario: NativeWorkflowCliFixture.Scenario, session: String): Vector[(String, C.Receipt)] =
    receipts(scenario, session).flatMap(receipt => {
      val value = receipt.payload.value
      val last = if (receipt.reference.identity.contains("validation-result-") ||
          receipt.reference.identity.matches(".*-check-[0-9]+-result"))
        value.validation.map(_.receipts).orElse(value.purposes.map(_.receipts)).flatMap(_.lastOption)
        else None
      last.filter(_.sbt.nonEmpty).map(receipt.reference.identity -> _)
    }).sortBy(_._1)

  "RS-FORMAL-02 isolated public CLI" should {
    for (purpose <- Vector(true, false)) {
      s"retain the failed ${if (purpose) "Purpose" else "merge"} receipt and run one new attempt after host correction" in {
        Given("a tiny real candidate whose first product check succeeds and second SBT launcher exits nonzero under an invalid JVM launch option")
        val stage = episode_stage("formalFailure")
        val route = if (purpose) "purpose" else "merge"
        val reopened = Option.when(stage == "after")(reopen_episode("formalFailure", route))
        val scenario = reopened.fold(_scenario("formal-" + route, purpose))(_._1)
        val repository = Path.of(scenario.host.target.root)
        if (purpose && stage != "after") Files.writeString(repository.resolve("value.txt"), "expected\n")
        val session = reopened.fold(start(scenario))(_._2)
        val failedcall = if (stage == "after") None else {
          When("ordinary Advance observes the real isolated candidate")
          val first = advance(scenario, session)
          Some(if (purpose) {
            Then("the runtime requests a Purpose result")
            first.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
            val request = purpose_inputs(scenario).head
            val result = P.Result("resolved", "Fixture-authored actual product purpose", Vector(P.Group("product-input",
              "Correct the actual product input", "fix: retain expected product input", Vector.empty,
              Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty, "The isolated product consumes value.txt")),
              request.files.map(_.path).filterNot(_ == "value.txt"))
            When("the fixture submits its actual product purpose")
            val submitted = submit_purpose(scenario, session, result)
            Then("the runtime accepts the Purpose result")
            submitted.business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
            When("ordinary Advance checks the resulting candidate under the broken host context")
            advance(scenario, session)
          } else first)
        }
        val old = current(scenario, session)
        val original = _formal(scenario, session)
        Then("the original receipts retain a successful prefix and a nonzero formal failure")
        original.map(_._2.identity).toSet shouldBe Set("formal-compile", "formal-test")
        original.find(_._2.identity == "formal-test").get._2.exitCode.exists(_ != 0) shouldBe true
        if (stage == "before") {
          When("the old installed runtime episode and its actual stdout are retained before publication")
          retain_episode("formalFailure", route, scenario, session, failedcall.get.invocation.stdout)
          Then("the retained session remains active with both original formal receipts")
          old.lifecycle shouldBe Lifecycle.Active
          original.size shouldBe 2
        } else {
          val originalrepaircount = old.history.flatMap(_.suspension).count(_.continuationIdentity.value.contains(":native-repair:"))
          val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
          val index = git(scenario.root, "-C", repository.toString, "write-tree")
          When("the host is corrected and public description observes its actual resolved context")
          configure(scenario, Map("textus.subsystem.process-execution.plan" -> _plan(scenario, purpose, broken = false).noSpaces))
          val described = describe(scenario, session)
          val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
          val target = N.decodeTarget(parse(take(description.target.toJsonStringC)).toOption.get).fold(fail(_), identity)
          Then("the target binds the immutable failed check and successful prefix")
          target.failed shouldBe original.find(_._2.identity == "formal-test").get._2
          target.successfulPrefix.last.identity shouldBe "formal-compile"
          When("prepared direction is submitted after the described host plan changes again")
          val input = direction(scenario, session, described, N.choices.head, target.pending.nonEmpty, target.pending.nonEmpty)
          val changedplan = _plan(scenario, purpose, broken = false).mapObject(fields => fields.add("sbtPrograms",
            fields("sbtPrograms").get.mapArray(_.map(_.mapObject(program => program.add("limits",
              program("limits").get.mapObject(_.add("executionTimeoutMillis", Json.fromLong(179999L)))))))))
          configure(scenario, Map("textus.subsystem.process-execution.plan" -> changedplan.noSpaces))
          val refused = adopt(scenario, input)
          Then("the changed host context is refused without changing the session")
          refused.exitCode should not be 0
          current(scenario, session) shouldBe old
          When("the exact described host context is restored and its prepared direction is adopted")
          configure(scenario, Map("textus.subsystem.process-execution.plan" -> _plan(scenario, purpose, broken = false).noSpaces))
          val adoption = adopt(scenario, input)
          Then("adoption appends direction without replaying checks, modifying Git or incrementing repair counters")
          adoption.business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "adopted"
          _formal(scenario, session) shouldBe original
          current(scenario, session).history.flatMap(_.suspension).count(_.continuationIdentity.value.contains(":native-repair:")) shouldBe originalrepaircount
          git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
          git(scenario.root, "-C", repository.toString, "write-tree") shouldBe index
          When("the same prepared direction is submitted again")
          val repeated = adopt(scenario, input)
          Then("the direction is reported as already adopted")
          repeated.business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
          if (target.pending.nonEmpty) {
            val reference = V.WorkflowArtifactReference("stale-old-repair-result", "1")
            When("the old merge worker submits its superseded repair result")
            val stale = scenario.repositoryCall("submitNativeMergeRepair", Vector("--sessionId", session, "--result",
              "{\"decision\":\"decision-needed\",\"rationale\":\"Stale old worker proposal\",\"replacements\":[]}",
              "--resultReference", json(reference), "--executionEvidence",
              json(V.WorkflowExecutionEvidence(Vector(reference), Some("stale-old-worker"), None, None))))
            Then("the stale repair result is refused")
            stale.exitCode should not be 0
          }
          When("separate normal Advance uses the same candidate under the exact adopted host context")
          val next = advance(scenario, session)
          Then("only the failed check obtains a new attempt and the successful prefix and old failure are immutable")
          withClue(next.invocation.logs) { next.exitCode shouldBe 0 }
          val after = _formal(scenario, session)
          original.foreach(value => after should contain(value))
          after.count(_._2.identity == "formal-compile") shouldBe 1
          after.count(_._2.identity == "formal-test") shouldBe 2
          after.find(value => value._1.startsWith(session + ":native-" + N.namespace(target)) || value._1.contains("-host-" + target.frontierRevision))
            .exists(_._2.exitCode.contains(0)) shouldBe true
          When("the old target is submitted after the new validation attempt")
          val obsolete = adopt(scenario, input)
          Then("the obsolete target is refused")
          obsolete.exitCode should not be 0
        }
      }
    }
  }
}
