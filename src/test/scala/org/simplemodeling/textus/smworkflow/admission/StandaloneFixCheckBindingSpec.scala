/**
 * @since Nov. 22, 2026
 * @version Nov. 22, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class StandaloneFixCheckBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Explicit standalone FixCheck" should {
    "select the binding for the Session checkout independently of check order" in {
      Given("a main checkout bound at ordinal 1 and a Session worktree bound at ordinal 3")
      val root = "/tmp/standard-fix-check-worktree"
      val otherroot = "/tmp/standard-fix-check-main"
      def workspace(ordinal: Int, path: String): Json =
        Json.obj("identity" -> Json.fromString(s"sm-workflow-repository-validation-$ordinal"), "root" -> Json.fromString(path))
      def program(ordinal: Int): Json =
        Json.obj("target" -> Json.fromString(s"sm-workflow-repository-validation-$ordinal"), "operation" -> Json.fromString("fix-check"))
      val plan = Json.obj("sbtPrograms" -> Json.arr(program(1), program(3)),
        "workspaces" -> Json.arr(workspace(1, otherroot), workspace(3, root)))

      When("FixCheck is explicitly selected while automatic lint is disabled")
      val selected = StandardWorkflowRuntime.boundedChecksC(Vector("fix-check"), false, plan, root)

      Then("only the worktree's configured FixCheck ordinal is selected")
      selected.toOption.map(_.map(_.ordinal)) shouldBe Some(Vector(3))
      StandardWorkflowRuntime.boundedChecksC(Vector("fix-check"), false, plan, "/tmp/unconfigured").isSuccess shouldBe false
    }
  }
}
