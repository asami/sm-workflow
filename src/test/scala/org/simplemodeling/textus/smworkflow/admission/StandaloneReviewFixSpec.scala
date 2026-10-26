/**
 * @since   Oct. 26, 2026
 * @version Oct. 26, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeStandaloneReviewFixStartInput
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import StandaloneReviewFix as R
import WorkflowSbtValidation as T
import RepositorySyncNativeMergeValidation as B

/** Fixture-owned check facts exercise bounded disposition admission. The public
  * Start/Advance episode uses actual isolated typed Git observations; it claims
  * no installed repair acceptance, behavioral process success or reviewer facts.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class StandaloneReviewFixSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail("Review-fix fixture admission failed: " + other)
  }
  private val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, T.Kind.Compile),
    GoalPhaseNativeValidation.Check("lint", 2, T.Kind.FixCheck), GoalPhaseNativeValidation.Check("focused", 3, T.Kind.TestOnly))
  private val source = ContextReference("original-review", "7")
  private val origin = R.Origin(source, "Original scoped review context and settled decisions", Vector(
    R.Finding("finding-1", "Correct the selected boundary", Vector("src/main/scala/Behavior.scala"), Vector(ContextReference("original-spec", "2"))),
    R.Finding("finding-2", "Cover the selected regression", Vector("src/test/scala/BehaviorSpec.scala"), Vector.empty)))
  private val input = S.Input("sm-workflow", "review-selected-findings", "Resolve only the adopted original review findings",
    Vector("src"), checks.map(_.identity), Vector("behavior"), Vector(ContextReference("request", "1")), false, reviewOrigin = Some(origin))
  private val initial = S.Candidate("a" * 40, "b" * 40, Vector.empty, "")
  private val candidate = initial.copy(tree = "c" * 40,
    paths = Vector("src/main/scala/Behavior.scala", "src/test/scala/BehaviorSpec.scala"), diff = "fixture-owned attributed repair")
  private val dispositions = Vector(
    R.Disposition("finding-1", "fixed", "mechanical", "Boundary repaired and selected check verifies it", Vector(candidate.paths.head), Vector("focused")),
    R.Disposition("finding-2", "fixed", "mechanical", "Regression covered by selected check", Vector(candidate.paths(1)), Vector("focused")))
  private val completed = S.Result("completed", "Resolved the adopted findings", Vector.empty,
    Vector(S.Condition("behavior", true, "Selected behavior addressed")), Vector.empty, reviewDispositions = Some(dispositions))
  private def originalRepair = take(S.candidateC(take(S.startC(input, "participant", checks)), initial))
  private def repaired(result: S.Result = completed, state: S.State = originalRepair) = take(S.completedC(
    S.Work("review-fix-fixture", "12", "Repair", state), result, ContextReference("semantic-repair", "1")))
  private def receipt(check: GoalPhaseNativeValidation.Check, success: Boolean = true) = B.Receipt(check.identity,
    "exited", Some(if (success) 0 else 1), if (success) "" else "[error] (Compile / compileIncremental) Compilation failed", "",
    Some(T.Facts(check.kind, if (success) T.Outcome.Succeeded else T.Outcome.Failed,
      if (success && check.kind == T.Kind.TestOnly) Some(T.Selection(Vector("fixture.BehaviorSpec"), 1, 2)) else None, None)))
  private def observe(state: S.State, check: GoalPhaseNativeValidation.Check) = take(S.observedC(take(S.intentC(state)), receipt(check)))
  private def validated(result: S.Result = completed) = checks.foldLeft(take(S.candidateC(repaired(result), candidate)))(observe)

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths)

  "Standalone original-review repair" should {
    "retain original finding identities and issue repair without requiring a failed check or another lifecycle" in {
      val state = originalRepair
      state.stage shouldBe "Repair"
      state.input.reviewOrigin shouldBe Some(origin)
      state.findings.map(_.takeWhile(_ != ':')) shouldBe origin.findings.map(_.identity)
      state.receipts shouldBe empty
      state.automaticCount shouldBe 0
      state.input.task shouldBe None
      state.reviewReference shouldBe None
      R.references(origin) should contain(source)
    }

    "accept mechanically established dispositions only after actual-candidate attribution and a nonempty successful check batch" in {
      Given("a fixture-owned semantic repair covering every original finding and selected verification check")
      val state = validated()
      Then("native assessment retains exact finding attribution and leaves the standalone repair uncommitted without a review waiver call")
      state.stage shouldBe "Accepted"
      state.reviewReference shouldBe None
      state.reviewDispositions shouldBe Some(dispositions)
      state.findings shouldBe empty
      state.receipts.map(_.identity) shouldBe input.checks
      state.automaticCount shouldBe 1
      state.ordinaryCount shouldBe 1
      state.input.reviewOrigin shouldBe Some(origin)
      state.commit shouldBe None
      S.committedC(state.copy(stage = "CommitIntent"), S.Commit("d" * 40, initial.head, candidate.tree)).isSuccess shouldBe false
    }

    "reject duplicate or crossed original findings and refuse missing, foreign or unverified dispositions" in {
      S.inputC(input.copy(reviewOrigin = Some(origin.copy(findings = Vector(origin.findings.head, origin.findings.head))))).isSuccess shouldBe false
      S.inputC(input.copy(reviewOrigin = Some(origin.copy(findings = Vector(origin.findings.head.copy(paths = Vector("unrelated/File.scala"))))))).isSuccess shouldBe false
      S.inputC(input.copy(task = Some(S.Task("journal", Vector.empty)))).isSuccess shouldBe false
      val work = S.Work("review-fix-fixture", "12", "Repair", originalRepair)
      def changedFirst(value: R.Disposition) = completed.copy(reviewDispositions = Some(dispositions.updated(0, value)))
      val invalidResults = Vector(completed.copy(reviewDispositions = None),
        completed.copy(reviewDispositions = Some(dispositions.reverse)),
        changedFirst(dispositions.head.copy(identity = "foreign")),
        changedFirst(dispositions.head.copy(checks = Vector("unselected"))),
        changedFirst(dispositions.head.copy(checks = Vector("compile"))),
        changedFirst(dispositions.head.copy(changedPaths = Vector("unrelated/File.scala"))))
      invalidResults.foreach(result => S.admitResultC(work, result).isSuccess shouldBe false)
      val unattributed = take(S.candidateC(repaired(), candidate.copy(paths = candidate.paths :+ "src/Unattributed.scala")))
      unattributed.stage shouldBe "Human"
      unattributed.receipts shouldBe empty
    }

    "route unresolved meaning to one affected independent review and require a resolved exact accepting disposition" in {
      val unsettled = completed.copy(reviewDispositions = Some(dispositions.updated(1,
        dispositions(1).copy(outcome = "unresolved", basis = "semantic", rationale = "Expected regression meaning remains unsettled"))))
      val review = validated(unsettled)
      review.stage shouldBe "Review"
      review.reviewReference shouldBe None
      val work = S.Work("review-fix-fixture", "20", "Review", review)
      S.admitResultC(work, unsettled.copy(outcome = "accept")).isSuccess shouldBe false
      val accepted = take(S.completedC(work, completed.copy(outcome = "accept"), ContextReference("affected-independent-review", "1")))
      accepted.stage shouldBe "Accepted"
      accepted.reviewReference shouldBe Some(ContextReference("affected-independent-review", "1"))
      accepted.input.reviewOrigin.get.originalReview shouldBe source
      accepted.reviewDispositions shouldBe Some(dispositions)
    }

    "retain prior review and successful checks during same-unit finding repair without resetting counters" in {
      val selected = validated(completed.copy(unresolvedQuestions = Vector("Selected boundary requires independent judgment")))
      val finding = completed.copy(outcome = "repair", findings = Vector("finding-2: affected regression needs correction"),
        conditions = Vector(S.Condition("behavior", false, "Affected meaning requires repair")))
      val repair = take(S.completedC(S.Work("review-fix-fixture", "20", "Review", selected), finding, ContextReference("affected-review", "1")))
      val fixed = repaired(completed, repair)
      val reused = take(S.candidateC(fixed, candidate))
      reused.stage shouldBe "Review"
      reused.receipts shouldBe selected.receipts
      reused.priorReview shouldBe Some(finding)
      reused.priorReviewCandidate shouldBe Some(candidate)
      reused.automaticCount shouldBe 2
      reused.ordinaryCount shouldBe 2
      reused.input.reviewOrigin shouldBe Some(origin)
      S.intentC(reused).isSuccess shouldBe false
      reused.asJson.as[S.State].toOption shouldBe Some(reused)
    }

    "preserve compiler non-execution and UNKNOWN checks rather than close original findings from fabricated success" in {
      val selected = take(S.candidateC(repaired(), candidate))
      val failed = take(S.observedC(take(S.intentC(selected)), receipt(checks.head, false)))
      failed.stage shouldBe "Repair"
      failed.input.reviewOrigin shouldBe Some(origin)
      failed.reviewDispositions shouldBe Some(dispositions)
      failed.receipts.size shouldBe 1
      S.diagnostics(failed, "selected-revision").head.testExecution.status shouldBe "not-executed-because-compilation-failed"
      val unknown = take(S.observedC(take(S.intentC(selected)), receipt(checks.head).copy(sbt =
        Some(T.Facts(T.Kind.Compile, T.Outcome.NotExecuted, None, None)))))
      unknown.stage shouldBe "Wait"
      unknown.commit shouldBe None
      S.intentC(unknown).isSuccess shouldBe false
    }

    "admit a no-change not-applicable disposition without silently converting a review repair into a passing-test no-change" in {
      val nochange = completed.copy(reviewDispositions = Some(dispositions.map(_.copy(outcome = "not-applicable",
        changedPaths = Vector.empty, checks = Vector.empty, rationale = "Original condition is mechanically excluded by the adopted scope"))))
      val assessed = checks.foldLeft(take(S.candidateC(repaired(nochange), initial)))(observe)
      assessed.stage shouldBe "Accepted"
      assessed.semanticReference shouldBe Some(ContextReference("semantic-repair", "1"))
      assessed.reviewDispositions.get.map(_.outcome) shouldBe Vector("not-applicable", "not-applicable")
      assessed.commit shouldBe None
    }

    "connect passive preparation and the public original-review Start to the same actual standalone Repair WorkOrder" in {
      Given("an isolated ordinary repository and original review-finding input, with no SBT binding or Phase document")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/review-fix").toAbsolutePath), "episode-").toRealPath()
      val selectedPath = "src/test/scala/ProductCheck.scala"
      val host = ThreeWorkflowNormalHostFixture.definition(root)
      val publicInput = V.StandaloneReviewFixInput("sm-workflow", "public-review-fix", "Repair only original finding-1",
        NonEmptyVector.of(selectedPath), NonEmptyVector.of("test"), NonEmptyVector.of("behavior"),
        NonEmptyVector.of(V.WorkflowArtifactReference("adopted-request", "1")), false, "documentation", "off", 10,
        V.WorkflowArtifactReference("original-review", "7"), "Keep original review context and settled scope",
        NonEmptyVector.of(V.StandaloneReviewFixFinding("finding-1", "Repair selected regression", NonEmptyVector.of(selectedPath), Vector.empty)))
      val prepared = take(NativeStandaloneReviewFixStartInput.prepareC(Map("input" -> "adopted.json", "selection-id" -> "review-fix-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "public-review-fix-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), name =>
        if (name == "adopted.json") publicInput.toRecord().toJsonStringC
        else Consequence.stateConflict("Unselected review source read")))
      _with_runtime_(root, "ReviewFixStart", Some(host.copy(goal = host.goal.copy(candidatePaths = Vector(selectedPath))))) {
        (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "ReviewFixStart").size).flatMap { _ =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        def invoke(operation: String, properties: List[Property]): V.StandaloneFixView = read_value[V.StandaloneFixView](take(
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "StandaloneFixService",
            operation = operation, properties = properties), actor)))
        When("normal Start and Advance consume the adopted review-origin input")
        val started = invoke("startStandaloneReviewFix", List(Property("input", publicInput, None), Property("selectionId", "review-fix-selection", None),
          Property("participant", "participant", None), Property("idempotencyKey", "public-review-fix-start", None)))
        val issued = invoke("advanceStandaloneFix", List(Property("sessionId", started.sessionId, None)))
        val redisplayed = invoke("advanceStandaloneFix", List(Property("sessionId", started.sessionId, None)))
        val work = io.circe.parser.decode[S.Work](take(issued.work.get.toJsonStringC)).fold(e => fail(e.getMessage), identity)
        Then("the actual retained assignment uses the existing repair capability and standalone completion, with original sources intact")
        issued.kind shouldBe "Request"
        work.kind shouldBe "Repair"
        work.state.input.reviewOrigin.get.originalReview shouldBe source
        work.state.input.reviewOrigin.get.findings.map(_.identity) shouldBe Vector("finding-1")
        work.state.receipts shouldBe empty
        work.state.automaticCount shouldBe 0
        issued.submitOperation shouldBe Some("submitStandaloneFixWorkResult")
        issued.issuedWorkContext.get.requiredOperation shouldBe "standalone-fix-repair-capability"
        redisplayed.issuedWorkContext shouldBe issued.issuedWorkContext
        ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("StandaloneFixService.startStandaloneReviewFix")
      }
    }
  }
}
