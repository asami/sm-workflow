/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** Selection facts never substitute for native effects or Human authority. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPoliciesSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncPolicies as P
  "Ordinary synchronization policies" should {
    "resolve defaults, normal configured values and per-call overrides without changing another target" in {
      Given("the already resolved Component properties and optional ordinary call selections")
      take(P.selectC(Record.empty, None, None)) shouldBe P.Selection("local-first", "auto-commit")
      for (main <- P.mainValues; dirty <- P.dirtyValues) {
        val configured = take(P.selectC(Record.empty, Some(main), Some(dirty)))
        configured shouldBe P.Selection(main, dirty)
        take(P.selectC(Record.data("mainIntegration" -> main, "uncommittedPolicy" -> dirty),
          Some("direct"), Some("error"))) shouldBe configured
        P.route(configured, "work", "release") shouldBe "direct"
        P.route(configured, "main", "main") shouldBe "direct"
        P.route(configured, "work", "main") shouldBe main
        configured.asJson.as[P.Selection] shouldBe Right(configured)
      }
    }
    "reject empty, malformed and unknown selections without treating them as omission" in {
      for (value <- Vector[Any]("", "DIRECT", " direct", "local-first ", "unknown", 1, true, Json.Null)) {
        P.selectC(Record.data("mainIntegration" -> value), None, None).isSuccess shouldBe false
        P.selectC(Record.data("uncommittedPolicy" -> value), None, None).isSuccess shouldBe false
      }
      P.selectC(Record.empty, Some("unknown"), None).isSuccess shouldBe false
      P.selectC(Record.empty, None, Some("unknown")).isSuccess shouldBe false
    }
    "retain a shared main-target setup dependency without starting parallel route decisions" in {
      val first = RepositorySyncDirectoryList.Member("repository", "source", "/fixture/source", "work", "origin", "main", "endpoint", "one")
      val main = first.copy(workspace = "main", root = "/fixture/main", localBranch = "main", sessionId = "two")
      for (route <- Vector("local-first", "confirm")) {
        RepositorySyncDirectoryList.sharedTargetSetup(Vector(first, main), P.Selection(route, "auto-commit"))
          .get should include("SharedTargetSetupRequired")
      }
      RepositorySyncDirectoryList.sharedTargetSetup(Vector(first, main.copy(endpoint = "other")),
        P.Selection("confirm", "auto-commit")) shouldBe None
      RepositorySyncDirectoryList.sharedTargetSetup(Vector(first, main), P.Selection("direct", "error")) shouldBe None
      val sourceParticipant = RepositorySyncLocalMain.Participant(first.repositoryIdentity, first.workspace, first.root,
        first.localBranch, first.remote, first.remoteBranch, first.endpoint)
      val mainParticipant = sourceParticipant.copy(workspace = main.workspace, root = main.root, branch = "main")
      val captured = first.copy(localMain = Some(RepositorySyncLocalMain.Plan(sourceParticipant, mainParticipant, true)))
      RepositorySyncDirectoryList.sharedTargetSetup(Vector(captured, main), P.Selection("local-first", "auto-commit")) shouldBe None
      val other = captured.copy(workspace = "second", root = "/fixture/second", sessionId = "three",
        localMain = captured.localMain.map(_.copy(main = mainParticipant.copy(root = "/fixture/another-main"))))
      RepositorySyncDirectoryList.sharedTargetSetup(Vector(captured, other, main), P.Selection("confirm", "auto-commit"))
        .get should include("SharedTargetSetupRequired")
    }
    "reject cyclic contributions and crossed consumer workspaces before selecting a batch route" in {
      import RepositorySyncDirectoryList.*
      import RepositorySyncLocalMain.{Cohort, Participant, Plan}

      val provider = Member("provider", "provider-source", "/fixture/provider", "work", "origin", "main", "endpoint", "B")
      val consumer = provider.copy(repositoryIdentity = "consumer", workspace = "consumer-source", root = "/fixture/consumer", sessionId = "A")
      val independent = provider.copy(repositoryIdentity = "independent", workspace = "independent", root = "/fixture/other", sessionId = "C")
      val main = Participant("canonical", "canonical-main", "/fixture/main", "main", "origin", "main", "endpoint")
      def source(member: Member) = Participant(member.repositoryIdentity, member.workspace, member.root,
        member.localBranch, member.remote, member.remoteBranch, member.endpoint)
      val dependencies = Map(consumer.sessionId -> Vector(provider.sessionId))
      for (ordered <- Vector(Vector(provider, consumer), Vector(consumer, provider))) {
        val ids = ordered.map(_.sessionId)
        val cohort = Cohort(ids.head, ordered.map(source), ids, ids)
        val members = ordered.map(member => member.copy(localMain = Some(Plan(source(member), main, true, cohort = Some(cohort)))))
        dependencyCohortSetup(members, dependencies).get should include("cannot converge")
      }
      val localConsumer = consumer.copy(localMain = Some(Plan(source(consumer), main, true)))
      dependencyCohortSetup(Vector(localConsumer, provider), dependencies).get should include("another workspace")
      val directBatch = Vector(consumer, provider, independent)
      dependencyCohortSetup(directBatch, dependencies) shouldBe None
      val captured = Payload("participant", directBatch, Vector.empty, "Ready", None, dependencies = dependencies)
      directBatch.filter(selectedDependencyMember(captured, _)).map(_.sessionId) shouldBe Vector("A", "B")
    }
    "report the exact required check when the selected consumer envelope cannot be passed to it" in {
      import RepositorySyncNativeMergeValidation.{Check, scopedValidationSetup}
      import WorkflowSbtValidation.Kind

      val supported = Vector(Check("compile", 1, Kind.Compile), Check("test-compile", 2, Kind.TestCompile),
        Check("targeted", 3, Kind.TestOnly), Check("ordinary", 4, Kind.Test, ordinaryDiscovery = true))
      scopedValidationSetup(supported) shouldBe None
      val blocked = scopedValidationSetup(supported ++ Vector(Check("required-lint", 5, Kind.FixCheck),
        Check("legacy-aggregate", 6, Kind.Test))).get
      blocked should include("required-lint (fix-check)")
      blocked should include("legacy-aggregate (test)")
      blocked should not include ("targeted")
    }
  }
}
