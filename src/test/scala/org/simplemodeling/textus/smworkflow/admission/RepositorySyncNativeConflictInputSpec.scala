package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
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
 *  version Oct. 24, 2026
 * @version Nov.  3, 2026
 * @author  ASAMI, Tomoharu
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncNativeConflictInputSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  override protected def conflict_path(scenario: String): Option[String] =
    if (scenario == "ConflictTyped") Some("name\nwith\ttab") else super.conflict_path(scenario)
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString))

  "Native conflict input and closed proposal contract" should {
    "admit all 33 assigned conflicts while refusing partial foreign duplicate and invalid-text resolutions" in {
      Given("33 distinct in-policy conflicts with complete ordinary native text")
      val blob = I.Blob("b" * 40, "source\n")
      val conflicts = (1 to 33).map(ordinal => I.Conflict("conflict-" + ordinal,
        f"file$ordinal%03d.txt", Some(blob), blob, blob)).toVector
      val request = I.Request("instance", "8", "c" * 40, "d" * 40, conflicts)
      val policy = I.Policy(conflicts.map(_.path).toSet)
      val result = S.Result("resolved", conflicts.map(value => S.Resolution(value.identity, "resolved\n")),
        "Resolve the complete assigned conflict inventory")
      val malformed = Vector(result.copy(resolutions = result.resolutions.dropRight(1)),
        result.copy(resolutions = result.resolutions.reverse),
        result.copy(resolutions = result.resolutions.updated(32, result.resolutions.head)),
        result.copy(resolutions = result.resolutions.updated(32, S.Resolution("foreign", "text"))),
        result.copy(resolutions = result.resolutions.updated(32, S.Resolution("conflict-33", "\u0000"))),
        result.copy(resolutions = result.resolutions.updated(32, S.Resolution("conflict-33", "\ud800"))))
      When("semantic admission consumes the complete inventory and malformed alternatives")
      val admitted = S.admitC(request, result, policy)
      val refused = malformed.map(S.admitC(request, _, policy)) ++ Vector(
        S.admitC(request, result, null), S.admitC(request, result, I.Policy(null)),
        S.admitC(request, result, I.Policy(Set("../outside.txt"))),
        S.admitC(request, result, policy.copy(semanticPaths = policy.semanticPaths - conflicts.last.path)),
        S.admitC(request.copy(conflicts = conflicts.updated(32, conflicts.head)), result, policy))
      Then("all 33 proposals survive together and exact coverage path policy and ordinary text remain mandatory")
      admitted.isSuccess shouldBe true
      S.inputCodec.decode(S.inputCodec.encode(request)) shouldBe Right(request)
      S.resultCodec.decode(S.resultCodec.encode(result)) shouldBe Right(result)
      refused.foreach(_.isSuccess shouldBe false)
    }
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
        Vector(I.Conflict("conflict-1", "shared.txt", Some(blob), blob, blob)))
      val policy = I.Policy(Set("shared.txt"))
      val result = S.Result("resolved", Vector(S.Resolution("conflict-1", "resolved\n")), "assigned resolution")
      When("the semantic outcome is deterministically admitted against the actual assigned input")
      val admitted = S.admitC(request, result, policy)
      Then("ordered exact coverage is required; DecisionNeeded cannot also choose a solution")
      admitted.isSuccess shouldBe true
      S.admitC(request, result.copy(resolutions = Vector.empty), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(resolutions = Vector(S.Resolution("other", "x"))), policy).isSuccess shouldBe false
      val large = result.copy(resolutions = Vector(S.Resolution("conflict-1", "界" * 40000)),
        rationale = "理由" * 40000)
      S.admitC(request, large, policy).isSuccess shouldBe true
      S.resultCodec.decode(S.resultCodec.encode(large)) shouldBe Right(large)
      S.admitC(request, result.copy(resolutions = Vector(S.Resolution("conflict-1", "\u0000"))), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(resolutions = Vector(S.Resolution("conflict-1", "\ud800"))), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(decision = "decision-needed", resolutions = Vector.empty), policy).isSuccess shouldBe true
      S.admitC(request, result.copy(decision = "decision-needed"), policy).isSuccess shouldBe false
      S.admitC(request, result, I.noSemanticPaths).isSuccess shouldBe false
    }
    "retain the v1 input wire for real bases and reject absent or null legacy bases" in {
      Given("a legacy modify/modify request with an actual base blob")
      val blob = I.Blob("b" * 40, "source\n")
      val request = I.Request("instance", "8", "c" * 40, "d" * 40,
        Vector(I.Conflict("conflict-1", "shared.txt", Some(blob), blob, blob)))
      val human = RepositorySyncHumanConflict.Input(request, "requesting-result", "1", "Needs Human direction")
      When("the semantic and Human inputs are encoded and their legacy base fields are removed or null")
      val semanticwire = S.inputCodec.encode(request)
      val humanwire = RepositorySyncHumanConflict.inputCodec.encode(human)
      def _alter_base_(wire: Json, nested: Boolean, absent: Boolean): Json = {
        def _alter_request_(value: Json): Json = value.mapObject(fields => fields.add("conflicts",
          fields("conflicts").get.mapArray(_.map(_.mapObject(conflict =>
            if (absent) conflict.remove("base") else conflict.add("base", Json.Null))))))
        wire.mapObject(fields => fields.add("payload", if (nested)
          fields("payload").get.mapObject(payload => payload.add("conflict", _alter_request_(payload("conflict").get)))
          else _alter_request_(fields("payload").get)))
      }
      Then("v1 retains its object shape and both decoders refuse a missing actual base")
      semanticwire.hcursor.get[String]("schemaVersion").toOption.get shouldBe "sm-workflow.repository-sync-conflict-request.v1"
      semanticwire.hcursor.downField("payload").downField("conflicts").downArray.downField("base")
        .get[String]("identity").toOption shouldBe Some(blob.identity)
      S.inputCodec.decode(semanticwire) shouldBe Right(request)
      humanwire.hcursor.get[String]("schemaVersion").toOption.get shouldBe "sm-workflow.repository-human-conflict-input.v1"
      RepositorySyncHumanConflict.inputCodec.decode(humanwire) shouldBe Right(human)
      for (absent <- Vector(false, true)) {
        S.inputCodec.decode(_alter_base_(semanticwire, false, absent)).isLeft shouldBe true
        RepositorySyncHumanConflict.inputCodec.decode(_alter_base_(humanwire, true, absent)).isLeft shouldBe true
      }
      S.inputCodec.decode(semanticwire.mapObject(_.add("schemaVersion", Json.fromString("unknown")))).isLeft shouldBe true
      RepositorySyncHumanConflict.inputCodec.decode(humanwire.mapObject(_.add("schemaVersion", Json.fromString("unknown"))))
        .isLeft shouldBe true
    }
    "round trip mixed v2 inputs while distinguishing an empty real base from an absent base" in {
      Given("an actual empty-text base alongside an add/add conflict with no base")
      val empty = I.Blob("b" * 40, "")
      val local = I.Blob("c" * 40, "local\n")
      val remote = I.Blob("d" * 40, "remote\n")
      val request = I.Request("instance", "8", "e" * 40, "f" * 40, Vector(
        I.Conflict("conflict-1", "empty-base.txt", Some(empty), local, remote),
        I.Conflict("conflict-2", "added.txt", None, local, remote)))
      val human = RepositorySyncHumanConflict.Input(request, "requesting-result", "1", "Needs Human direction")
      When("the mixed semantic and nested Human requests are encoded and decoded")
      val semanticwire = S.inputCodec.encode(request)
      val humanwire = RepositorySyncHumanConflict.inputCodec.encode(human)
      Then("v2 preserves both cases without inventing a base or changing result schemas")
      semanticwire.hcursor.get[String]("schemaVersion").toOption.get shouldBe "sm-workflow.repository-sync-conflict-request.v2"
      humanwire.hcursor.get[String]("schemaVersion").toOption.get shouldBe "sm-workflow.repository-human-conflict-input.v2"
      S.inputCodec.decode(semanticwire) shouldBe Right(request)
      RepositorySyncHumanConflict.inputCodec.decode(humanwire) shouldBe Right(human)
      val conflicts = semanticwire.hcursor.downField("payload").get[Vector[Json]]("conflicts").toOption.get
      conflicts.head.hcursor.downField("base").get[String]("text").toOption shouldBe Some("")
      conflicts.last.hcursor.downField("base").focus shouldBe Some(Json.Null)
      val missing = semanticwire.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(payload =>
        payload.add("conflicts", Json.fromValues(conflicts.updated(1, conflicts.last.mapObject(_.remove("base"))))))))
      S.inputCodec.decode(missing) shouldBe Right(request)
      val result = S.Result("resolved", request.conflicts.map(c => S.Resolution(c.identity, "resolved\n")), "Assigned proposal")
      val policy = I.Policy(request.conflicts.map(_.path).toSet)
      S.admitC(request, result, policy).isSuccess shouldBe true
      S.admitC(request, result.copy(resolutions = result.resolutions.reverse), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(resolutions = result.resolutions.take(1)), policy).isSuccess shouldBe false
      S.admitC(request, result, I.Policy(Set("empty-base.txt"))).isSuccess shouldBe false
      S.admitC(request, result.copy(resolutions = result.resolutions.updated(1,
        S.Resolution("conflict-2", "\u0000"))), policy).isSuccess shouldBe false
      S.resultCodec.encode(result).hcursor.get[String]("schemaVersion").toOption.get shouldBe "sm-workflow.repository-sync-conflict-result.v1"
      S.resultCodec.decode(S.resultCodec.encode(result)) shouldBe Right(result)
      val answer = RepositorySyncHumanConflict.Answer("ProvideResolution", result.resolutions, "Assigned Human answer fixture")
      RepositorySyncHumanConflict.admitAnswerC(human, answer, policy).isSuccess shouldBe true
      RepositorySyncHumanConflict.answerCodec.encode(answer).hcursor.get[String]("schemaVersion").toOption.get shouldBe
        "sm-workflow.repository-human-conflict-answer.v1"
      RepositorySyncHumanConflict.answerCodec.decode(RepositorySyncHumanConflict.answerCodec.encode(answer)) shouldBe Right(answer)
    }
    "bind a real base identity or truthful absence and refuse malformed optional Human bases" in {
      Given("a passive typed Human question fixture with its original conflict identity")
      val original = NativeHumanEvidenceFixture.repositoryQuestion
      val decision = original.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]]
      val input = decision.request.input.get.value
      val conflict = input.conflict.conflicts.head
      def _question_(base: Option[I.Blob]): WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing] =
        original.copy(current = decision.copy(request = decision.request.copy(input = Some(TypedValue(
          RepositorySyncHumanConflict.inputCodec.typeIdentity, input.copy(conflict = input.conflict.copy(
            conflicts = Vector(conflict.copy(base = base)))))))))
      When("the external evidence source binds present, absent and malformed optional bases")
      val present = take(ThreeWorkflowExternalEvidencePorts.repositoryHumanSourceC(original))
      val absent = take(ThreeWorkflowExternalEvidencePorts.repositoryHumanSourceC(_question_(None)))
      Then("the old string identity stays exact, absence is JSON null and malformed values are refused")
      present.hcursor.downField("conflict").downField("conflicts").downArray.get[String]("base").toOption shouldBe
        Some(conflict.base.get.identity)
      absent.hcursor.downField("conflict").downField("conflicts").downArray.downField("base").focus shouldBe Some(Json.Null)
      Vector[Option[I.Blob]](null, Some(null), Some(I.Blob(null, ""))).foreach { base =>
        ThreeWorkflowExternalEvidencePorts.repositoryHumanSourceC(_question_(base)).isSuccess shouldBe false
      }
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
