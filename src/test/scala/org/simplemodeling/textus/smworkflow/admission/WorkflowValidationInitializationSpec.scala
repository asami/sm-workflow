package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Path
import org.goldenport.cncf.config.ComponentInitializationParameters
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import SplitPhaseExplicitPartitionFixture.take

/** Shared defaults never create grants, precise coverage or successful results.
  * @since   Nov.  9, 2026
  * @version Nov. 28, 2026
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
    "require a fresh FixCheck for a new candidate when only coverage equivalence is known" in {
      Given("a prior successful check reference for an equivalent declared input scope")
      val lint = RepositorySyncNativeMergeValidation.Check("fix-check", 2, WorkflowSbtValidation.Kind.FixCheck)
      val compile = RepositorySyncNativeMergeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
      val prior = RepositorySyncProgramImpact.Reuse(lint.identity, "validation", "closure", None)
      When("validation considers reusing the prior reference for a newly selected check")
      RepositorySyncProgramImpact.reusableProductCheck(lint, prior, Vector.empty) shouldBe false
      RepositorySyncProgramImpact.reusableProductCheck(compile, prior.copy(check = compile.identity), Vector.empty) shouldBe true
      Then("reconstruction preserves only the exact previously selected FixCheck reference")
      RepositorySyncProgramImpact.reusableProductCheck(lint, prior, Vector(prior)) shouldBe true
      RepositorySyncProgramImpact.reusableProductCheck(lint, prior, Vector(prior.copy(validation = "other"))) shouldBe false
    }
    "connect a shared-lint project to an authorized FixCheck without a host lint flag" in {
      val declared = take(WorkflowProjectDeclaration.fromC(Some("existing fixed check-only build"),
        Some("validation:\n  fixCheck: true\n")))
      declared.get.lint.enabled shouldBe true
      val actual = take(I.assembleC(root, normal, declared, None, None))
      actual.program.get.checks.map(c => c.identity -> c.ordinal) shouldBe Vector("compile" -> 1, "fix-check" -> 2, "test" -> 3)
      I.assembleC(root, plan(Vector(1 -> "compile", 2 -> "fix-check", 3 -> "test"), grant = false), declared, None, None)
        .isSuccess shouldBe false
      take(I.assembleC(root, normal, project, None, None)).program.get.checks.map(_.identity) shouldBe Vector("compile", "test")
      // Selection/description neither mutates the supplied plan nor claims a
      // runtime execution, installation or consumer adoption.
      normal.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.forall(p => !p.hcursor.downField("lintInput").succeeded) shouldBe true
    }
    "omit only FixCheck when the host disables it" in {
      Given("a Scala project that declares FixCheck and an existing explicit program policy")
      val declared = take(WorkflowProjectDeclaration.fromC(Some("existing fixed check-only build"),
        Some("validation:\n  fixCheck: true\n")))
      val original = take(I.assembleC(root, normal, declared, None, None)).program.get
      val explicit = original.copy(coverage = original.coverage.filter(_.check.kind == WorkflowSbtValidation.Kind.FixCheck))
      val host = plan(Vector(1 -> "compile", 3 -> "test"))
      When("the retained start setting disables FixCheck")
      val selected = Vector(
        take(I.assembleC(root, host, declared, None, None, fixCheckEnabled = false)),
        take(I.assembleC(root, host, declared, Some(explicit), None, fixCheckEnabled = false)))
      Then("both policies still select actual Compile and Test even without a fixed FixCheck binding")
      selected.foreach { initialized =>
        initialized.projectProvidesFixCheck shouldBe false
        val validation = StandardWorkflowRuntime.normalRepositoryValidation(initialized.program,
          projectProvidesFixCheck = initialized.projectProvidesFixCheck)
        validation.assessment.get.requiresLint(Vector("src/main/scala/Example.scala")) shouldBe false
        RepositorySyncProgramImpact.select(initialized.program.get, assessment("src/main/scala/Example.scala")).checks
          .map(c => c.identity -> c.ordinal) shouldBe Vector("compile" -> 1, "test" -> 3)
      }
      explicit.fullChecks.map(_.identity) should contain("fix-check")
    }
    "retain focused coverage when disabled FixCheck was the only FULL check" in {
      import RepositorySyncProgramImpact as P
      import RepositorySyncNativeMergeValidation.Check
      import WorkflowSbtValidation.Kind
      Given("explicit Compile and Test coverage for sources and a FixCheck-only FULL declaration")
      val focused = Vector(Check("compile", 1, Kind.Compile), Check("test", 3))
      val lint = Check("fix-check", 2, Kind.FixCheck)
      val explicit = P.Policy(focused.map(P.Coverage(_, Vector("src"), Vector("src", "build.sbt"), "Declared source coverage")),
        Vector(lint), Vector("build.sbt"), "Declared FULL coverage", Vector.empty)
      When("a disabled Session projects the admitted policy and reads it back")
      val projected = take(I.assembleC(root, normal, project, Some(explicit), None, fixCheckEnabled = false)).program.get
      val retained = take(P.decodeC(projected.asJson))
      Then("focused source changes select the original Compile and Test")
      val selected = P.select(retained, assessment("src/main/scala/Product.scala"))
      selected.depth shouldBe "FOCUSED"
      selected.checks shouldBe focused
      selected.setupReason shouldBe None
      And("an actual FULL requirement waits without promoting focused coverage")
      val broad = P.select(retained, assessment("build.sbt"))
      broad.checks shouldBe empty
      broad.setupReason.get should include("requires FULL coverage")
      RepositorySyncNativeMergeValidation.setupReason(StandardWorkflowRuntime.normalRepositoryValidation(Some(retained)),
        assessment("build.sbt")) shouldBe broad.setupReason
    }
    "reject a configured raw FULL policy without coverage with the default or explicit setting" in {
      Given("an explicit Test declaration without focused coverage or ordinary discovery")
      val explicit = take(I.assembleC(root, normal, project, None, None)).program.get.copy(coverage = Vector.empty)
      When("the declaration is decoded and initialized")
      val decoded = RepositorySyncProgramImpact.decodeC(explicit.asJson)
      val default = I.assembleC(root, normal, project, Some(explicit), None)
      val disabled = I.assembleC(root, normal, project, Some(explicit), None, fixCheckEnabled = false)
      Then("empty configured coverage is refused before any runtime projection")
      decoded.isSuccess shouldBe false
      default.isSuccess shouldBe false
      disabled.isSuccess shouldBe false
    }
    "derive runnable FULL checks and declared input scopes without per-run JSON" in {
      Given("an ordinary SBT/CML project and its existing managed execution declarations")
      When("common initialization assembles available validation information")
      val initialized = take(I.assembleC(root, normal, project, None, None))
      Then("actual ordinal bindings and declared CML consumption reach delta selection")
      val p = initialized.program.get
      p.checks.map(c => c.identity -> c.ordinal) shouldBe Vector("compile" -> 1, "test" -> 3)
      initialized.documents.get.consumedPaths should contain("models/public.cml")
      RepositorySyncProgramImpact.select(p, assessment("src/main/scala/Product.scala")).depth shouldBe "FULL"
      RepositorySyncProgramImpact.select(p, assessment("custom/resource.conf")).checks shouldBe p.fullChecks
      p.priorEvidence shouldBe empty
    }
    "select inferred lint only for a project that declares its command and has a managed binding" in {
      Given("a project advertising its fixCheck command and a separate host execution profile")
      val lintproject = take(WorkflowProjectDeclaration.fromC(Some("addCommandAlias(\"fixCheck\", \"Compile / scalafix --check\")"),
        Some("validation:\n  fixCheck: true\n  sharedScalaLint: false\n")))
      val nolintbinding = plan(Vector(1 -> "compile", 3 -> "test"))
      When("fallback selection resolves each execution profile")
      val bound = take(I.assembleC(root, normal, lintproject, None, None)).program.get
      val unbound = take(I.assembleC(root, nolintbinding, lintproject, None, None)).program.get
      Then("the project and binding together select the applicable lint ordinal")
      bound.checks.map(c => c.identity -> c.ordinal) shouldBe Vector("compile" -> 1, "fix-check" -> 2, "test" -> 3)
      unbound.checks.map(_.identity) shouldBe Vector("compile", "test")
      take(I.assembleC(root, normal, project, Some(bound), None)).program shouldBe Some(bound)
    }
    "apply the same project declaration to inferred framework discovery" in {
      Given("framework discovery and lint sharing one admitted project workspace")
      val managed = plan(Vector(1 -> "test-only", 1 -> "fix-check")).mapObject(fields =>
        fields.add("sbtPrograms", fields("sbtPrograms").get.mapArray(rows =>
          rows.updated(0, rows.head.mapObject(_.add("discoveredTests", Json.True))))))
      val parameters = ComponentInitializationParameters.empty
      When("ordinary discovery is resolved with and without a project lint declaration")
      val declared = take(RepositorySyncProgramImpact.ordinaryPolicyC(parameters, root.toString, managed, providesFixCheck = true))
      val absent = take(RepositorySyncProgramImpact.ordinaryPolicyC(parameters, root.toString, managed, providesFixCheck = false))
      Then("only the declared project receives a lint check alongside its discovered tests")
      declared.get.fullChecks.map(_.identity) shouldBe Vector("ordinary-project-full", "scala-lint")
      declared.get.coverage.map(_.check.identity) shouldBe Vector("scala-lint")
      absent.get.checks.map(_.identity) shouldBe Vector("ordinary-project-full")
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
