package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.cncf.processexecution.*
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import RepositorySyncNativeConflictInput as I
import RepositorySyncConflictSemantic as S
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import SplitPhaseExplicitPartitionFixture.take

/** Actual typed unmerged-index facts mapped through the normal Component/UoW.
 * Decoder refusals for malformed, duplicate, partial, unsafe and invalid-stage
 * records reuse the qualified CNCF GitExecutionSpec producer examples.
 * @since   Oct.  6, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class RepositorySyncNativeConflictInputSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  override protected def conflict_path(scenario: String): Option[String] =
    if (scenario == "ConflictTyped") Some("name\nwith\ttab") else super.conflict_path(scenario)
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowHostParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString))

  "Native conflict input and closed proposal contract" should {
    "retain typed native stages, modes, object IDs and literal tab and newline paths" in {
      Given("owned conflicting repositories and a normal host with actual typed Git grants")
      val root = Files.createTempDirectory(Files.createDirectories(
        Path.of("target/sm-workflow/work/repository-conflict-input").toAbsolutePath.normalize), "episode-")
      val path = conflict_path("ConflictTyped").get
      _with_runtime_(root, "ConflictTyped") { (_, _, _, prepare) =>
        prepare(call => call.setupC(11))
        When("the current component fetches the remote tip and prepares its actual conflicting merge")
        prepare(call => for {
          _ <- E.executeGitC(call.core, GitOperation.Fetch).flatMap(_.valueC)
          before <- G.observeC(call.core)
          remote <- GitObjectId.parseC(before.trackingTip.get)
          merged <- E.executeGitC(call.core, GitOperation.PendingMerge(remote))
          entries <- I.entriesC(call.core)
          pending <- G.inspectC(call.core)
          _ = {
            Then("business mapping preserves all three native index stages and the exact literal filename")
            merged.outcome shouldBe GitExecutionOutcome.ConflictObservationRequired
            entries.size shouldBe 3
            entries.map(_.stage) shouldBe Vector(1, 2, 3)
            entries.map(_.mode) shouldBe Vector.fill(3)("100644")
            entries.map(_.path) shouldBe Vector.fill(3)(path)
            entries.foreach(entry => GitObjectId.parseC(entry.blob).isSuccess shouldBe true)
            pending.mergeHead shouldBe Some(remote.value)
            pending.observed.dirty.filter(_.unmerged).map(_.path) shouldBe Vector(path)
          }
        } yield ())
      }
    }
    "admit only complete assigned conflict-ID resolutions or an explicit non-consenting DecisionNeeded" in {
      Given("a selected native request and finite host path policy")
      val blob = I.Blob("b" * 40, "source\n")
      val request = I.Request("instance", "8", "c" * 40, "d" * 40,
        Vector(I.Conflict("conflict-1", "shared.txt", blob, blob, blob)))
      val policy = I.Policy(Set("shared.txt"), maximumBlobBytes = 32)
      val result = S.Result("resolved", Vector(S.Resolution("conflict-1", "resolved\n")), "assigned resolution")
      When("the semantic outcome is deterministically admitted against the actual assigned input")
      val admitted = S.admitC(request, result, policy)
      Then("ordered exact coverage and finite bytes are required; DecisionNeeded cannot also choose a solution")
      admitted.isSuccess shouldBe true
      S.admitC(request, result.copy(resolutions = Vector.empty), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(resolutions = Vector(S.Resolution("other", "x"))), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(resolutions = Vector(S.Resolution("conflict-1", "x" * 33))), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(decision = "decision-needed", resolutions = Vector.empty), policy).isSuccess shouldBe true
      S.admitC(request, result.copy(decision = "decision-needed"), policy).isSuccess shouldBe false
      S.admitC(request, result, I.noSemanticPaths).isSuccess shouldBe false
    }
    "consume actual semantic results without granting unused process annotations authority" in {
      Given("a valid typed semantic result with no command or transition authority")
      val result = S.Result("decision-needed", Vector.empty, "Human direction required")
      val wire = S.resultCodec.encode(result)
      When("unused next-state and executable diagnostics accompany the semantic result")
      val next = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get
        .mapObject(_.add("nextState", Json.fromString("Converged")))))
      val argv = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get
        .mapObject(_.add("argv", Json.arr(Json.fromString("push"))))))
      Then("only the same business result is decoded and no command or transition is selected")
      S.resultCodec.decode(wire) shouldBe Right(result)
      S.resultCodec.decode(next) shouldBe Right(result)
      S.resultCodec.decode(argv) shouldBe Right(result)
    }
  }
}
