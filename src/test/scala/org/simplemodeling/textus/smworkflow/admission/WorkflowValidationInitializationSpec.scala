package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.Path
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import SplitPhaseExplicitPartitionFixture.take

/** Shared defaults never create grants, precise coverage or successful results.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowValidationInitializationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import WorkflowValidationInitialization as I
  private val root = Path.of("/project")
  private val component = "org.simplemodeling.textus.SmWorkflow"
  private def plan(programs: Vector[(Int, String)], grant: Boolean = true, otherRoot: Boolean = false): Json = {
    def workspace(n: Int) = "sm-workflow-repository-validation-" + n
    def cap(n: Int, kind: String) = kind + "-" + n
    Json.obj("sbtPrograms" -> Json.fromValues(programs.map((n, kind) => Json.obj(
      "target" -> Json.fromString(workspace(n)), "operation" -> Json.fromString(kind), "capability" -> Json.fromString(cap(n, kind))))),
      "grants" -> Json.arr(Json.obj("componentId" -> Json.fromString(component), "capabilities" ->
        Json.fromValues(if (grant) programs.map((n, kind) => Json.fromString(cap(n, kind))) else Vector.empty))),
      "workspaces" -> Json.fromValues(programs.map(_._1).distinct.map(n => Json.obj("identity" -> Json.fromString(workspace(n)),
        "root" -> Json.fromString(if (otherRoot) "/other" else root.toString)))),
      "workspaceGrants" -> Json.fromValues(programs.map(_._1).distinct.map(n => Json.obj("componentId" -> Json.fromString(component),
        "workspaceId" -> Json.fromString(workspace(n)), "access" -> Json.fromString("manage")))))
  }
  private val normal = plan(Vector(1 -> "compile", 1 -> "fix-check", 2 -> "fix-check", 3 -> "test"))
  private val project = take(WorkflowProjectDeclaration.fromC(Some("scalaVersion := \"3.3.8\""), Some("cml:\n  source: models/public.cml\n")))
  private def assessment(paths: String*) = RepositorySyncDocumentInputs.Assessment(paths.toVector, Vector.empty, paths.toVector, Vector.empty, "actual merge delta")

  "Shared validation initialization" should {
    "derive runnable FULL checks and declared input scopes without per-run JSON" in {
      Given("an ordinary SBT/CML project and its existing managed execution declarations")
      When("common initialization assembles available validation information")
      val initialized = take(I.assembleC(root, normal, project, None, None))
      Then("actual ordinal bindings and declared CML consumption reach delta selection")
      val p = initialized.program.get
      p.checks.map(c => c.identity -> c.ordinal) shouldBe Vector("compile" -> 1, "fix-check" -> 2, "test" -> 3)
      initialized.documents.get.consumedPaths should contain("models/public.cml")
      RepositorySyncProgramImpact.select(p, assessment("src/main/scala/Product.scala")).depth shouldBe "FULL"
      RepositorySyncProgramImpact.select(p, assessment("custom/resource.conf")).checks shouldBe p.fullChecks
      p.priorEvidence shouldBe empty
    }
    "preserve independent explicit policy and evidence overrides" in {
      Given("a declared coverage policy with genuine native record locators")
      val inferred = take(I.assembleC(root, normal, project, None, None))
      val prior = RepositorySyncProgramImpact.Prior("session", "session:ordered-validation:1:validation", "session:ordered-step-closure:1")
      val explicit = inferred.program.get.copy(reason = "Explicit project coverage", priorEvidence = Vector(prior),
        coverage = inferred.program.get.coverage.map(c => c.copy(inputPaths = c.inputPaths :+ "README.md")))
      val documents = RepositorySyncDocumentInputs.Policy(Vector("manual"), Vector("README.md"), "README is consumed in this project")
      When("each override is initialized independently")
      val programOnly = take(I.assembleC(root, normal, project, Some(explicit), None))
      val documentOnly = take(I.assembleC(root, normal, project, None, Some(documents)))
      Then("an explicit declaration wins without dropping existing evidence or guessing PASS")
      programOnly.program shouldBe Some(explicit)
      programOnly.documents.get.documentPaths should contain("README.md")
      programOnly.documents.get.consumedPaths should contain("README.md")
      documentOnly.documents shouldBe Some(documents)
      documentOnly.program shouldBe inferred.program
    }
    "leave unknown projects and unavailable execution declarations unresolved" in {
      Given("a missing project marker or checks from a different target or without grants")
      When("defaults are considered without executing any checks")
      val results = Vector(take(I.assembleC(root, normal, None, None, None)),
        take(I.assembleC(root, plan(Vector(1 -> "compile", 2 -> "test"), grant = false), project, None, None)),
        take(I.assembleC(root, plan(Vector(1 -> "compile", 2 -> "test"), otherRoot = true), project, None, None)))
      Then("no runnable program policy, authority or result is invented")
      results.foreach(_.program shouldBe None)
    }
    "preserve unknown declaration semantics without exempting possibly consumed documents" in {
      Given("a present malformed declaration or an unsupported CML source")
      When("optional project discovery cannot establish its consumed inputs")
      val malformed = take(WorkflowProjectDeclaration.fromC(Some("build"), Some("cml: [")))
      val unsupported = take(WorkflowProjectDeclaration.fromC(Some("build"), Some("cml:\n  source: ../outside.md\n")))
      Then("neither a runnable policy nor a document exemption is inferred")
      Vector(malformed, unsupported).foreach { declaration =>
        val initialized = take(I.assembleC(root, normal, declaration, None, None))
        initialized.program shouldBe None
        initialized.documents shouldBe None
      }
    }
  }
}
