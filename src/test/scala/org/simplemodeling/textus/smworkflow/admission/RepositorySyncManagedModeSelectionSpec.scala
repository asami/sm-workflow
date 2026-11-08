package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Path
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeMetadata.given
import RepositorySyncNativeMergeValidation as V
import RepositorySyncProgramImpact as P

/** Consumer binding over explicitly scoped synthetic inventory; no build or merge coverage claim.
 * @since   Nov.  6, 2026
 * @version Nov.  8, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
class RepositorySyncManagedModeSelectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _scope = D.DiscoveryScope("project-tests", "1", Vector(D.ArtifactScope("project", "project-tests", "1",
    "candidate-tree", Path.of("/project/target/test-classes"), "project.spec")))
  private val _reference = D.OperationReference("project", "spec", "case")
  private val _metadata = M.resolveC(None, Some(M.Declaration(requiredSemantics = Set("executionModes"),
    executionModes = Some(Set("dev", "production"))))).toOption.get
  private val _operation = D.Operation(_reference, D.Provenance("project-tests", "1", "candidate-tree", "1"),
    D.ScalaTestSelector("project.spec.ProductSpec", "selected case"), Some(_metadata), Vector.empty,
    Some(D.FrameworkFacts(Set.empty, false)))
  private val _inventory = D.Inventory(_scope, D.Completeness.COMPLETE, Vector(_operation), Vector.empty)
  private val _profile = GoalPhaseNativeMetadata.Profile(_scope, D.SelectionQuery(standardFramework = true), Vector.empty)
  private val _check = V.Check("project-full", 1, WorkflowSbtValidation.Kind.TestOnly,
    workspace = Some("ordinary-project"), metadata = Some(_profile))

  "Ordinary repository metadata selection" should {
    "bind required variants to the registered ordinary workspace without a second test registry" in {
      Given("a scoped synthetic inventory with two required application modes and one ordinary project binding")
      val selected = Vector("develop", "production").zipWithIndex.map { (mode, index) =>
        _check.copy(identity = _check.identity + "-operation-" + (index + 1),
          invocation = Some(V.Invocation(_check.identity, _inventory, _reference, mode)))
      }
      When("retained exact invocations are rebound through the shared producer owner")
      val bound = selected.map(check => P.operationC(check).toOption.get)
      Then("workspace, source, test and required mode remain attributable independently")
      bound.map(_.intent.target.workspaceId.value).distinct shouldBe Vector("ordinary-project")
      bound.map(_.intent.target.validationMode) shouldBe Vector(Some("develop"), Some("production"))
      bound.map(_.operation.provenance.sourceRevision).distinct shouldBe Vector("candidate-tree")
      selected.foreach(check => P.belongs(check, Vector(_check)) shouldBe true)
      P.operationC(selected.head.copy(invocation = selected.head.invocation.map(_.copy(mode = "demo")))).isFaillure shouldBe true
      P.operationC(selected.head.copy(metadata = Some(_profile.copy(scope = _scope.copy(revision = "foreign"))))).isFaillure shouldBe true
    }

    "retain canonical legacy check bytes and keep missing framework facts out of new standard FULL" in {
      Given("a historical check and an operation without newly captured framework evidence")
      val legacy = V.Check("historical", 1)
      val historical = _inventory.copy(operations = Vector(_operation.copy(framework = None)))
      When("existing codecs and the new opt-in standard selector are used")
      val wire = legacy.asJson
      val selected = D.selectC(historical, D.SelectionQuery(standardFramework = true)).toOption.get
      Then("legacy bytes gain no fabricated facts and cannot become new standard coverage")
      wire shouldBe Json.obj("identity" -> Json.fromString("historical"), "ordinal" -> Json.fromInt(1))
      wire.as[V.Check].toOption shouldBe Some(legacy)
      selected shouldBe empty
      D.selectC(historical, D.SelectionQuery()).toOption.get should have size 1
    }

    "resolve the ordinary declared TestOnly binding and refuse raw Test as new framework coverage" in {
      Given("an existing ordinary workspace and normal TestOnly declaration")
      val policy = P.Policy(Vector.empty, Vector(_check), Vector.empty, "Standard project FULL", Vector.empty)
      val plan = Json.obj("workspaces" -> Json.arr(Json.obj("identity" -> Json.fromString("ordinary-project"),
        "root" -> Json.fromString("/project"))), "sbtPrograms" -> Json.arr(Json.obj(
          "target" -> Json.fromString("ordinary-project"), "operation" -> Json.fromString("test-only"))))
      When("normal workspace resolution selects the already configured typed binding")
      val resolved = P.bindOrdinaryC(policy, plan, "/project").toOption.get
      Then("the exact metadata declaration is retained and no sync-only host alias is installed")
      resolved.checks.head.workspace shouldBe Some("ordinary-project")
      resolved.checks.head.metadata shouldBe Some(_profile)
      val raw = policy.copy(fullChecks = Vector(V.Check("raw-test", 1)))
      P.validateC(raw).isFaillure shouldBe true
    }
  }
}
