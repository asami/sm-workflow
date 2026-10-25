package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets

/**
 * @since   Oct.  6, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseRuntimeCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val workflow = Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json"), StandardCharsets.UTF_8)
  private val producer = Files.readString(Path.of("target/cozy/candidate-admission-producer-abi.json"), StandardCharsets.UTF_8)
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"catalog admission failed: $other")
  }

  "GoalPhase runtime catalog" should {
    "load the complete official resource without a test-classpath or per-Workflow fixture fallback" in {
      Given("the generated main resources, optionally in the actual packageBin JAR")
      val location = sys.props.get("smworkflow.runtime.abi.jar").map(path => Path.of(path).toUri.toURL)
        .getOrElse(GoalPhaseRuntimeCatalog.getClass.getProtectionDomain.getCodeSource.getLocation)
      val loader = new URLClassLoader(Array(location), null)
      try {
        When("GoalPhase is selected using its compiled identity and version")
        val catalog = take(GoalPhaseRuntimeCatalog.loadC(loader))
        Then("the official resource resolves the executable GoalPhase definition")
        val official = Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json"), StandardCharsets.UTF_8)
        take(GoalPhaseRuntimeCatalog.loadC()).workflow.workflow.identity shouldBe "GoalPhaseWorkflow"
        official should include("SplitPhaseWorkflow")
        official should include("RepositorySyncWorkflow")
        catalog.workflow.workflow.identity shouldBe "GoalPhaseWorkflow"
        catalog.definition.validateC shouldBe a[Consequence.Success[?]]
        org.goldenport.cncf.workflow.CandidateWorkflowAbi.parseC(official, catalog.producer,
          "src/main/cozy/sm-workflow.cml") shouldBe a[Consequence.Failure[?]]
      } finally loader.close()
    }

    "reject absent or duplicate official runtime resources without falling back to checked-in fixtures" in {
      Given("an isolated empty classloader and a classloader with duplicate main resource URLs")
      val location = GoalPhaseRuntimeCatalog.getClass.getProtectionDomain.getCodeSource.getLocation
      val empty = new URLClassLoader(Array.empty[java.net.URL], null)
      val parent = new URLClassLoader(Array(location), null)
      val duplicate = new URLClassLoader(Array(location), parent)
      try {
        When("runtime catalog admission uses those resource owners")
        val results = Vector(GoalPhaseRuntimeCatalog.loadC(empty), GoalPhaseRuntimeCatalog.loadC(duplicate),
          GoalPhaseRuntimeCatalog.loadC(null))
        Then("none can advertise a managed catalog")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      } finally {
        duplicate.close()
        parent.close()
        empty.close()
      }
    }

    "resolve compiled execution definitions without retained input copies or fingerprints" in {
      Given("generated ABI text matching the compiled main descriptor")
      When("the pure runtime catalog parses the text")
      val catalog = take(GoalPhaseRuntimeCatalog.parseC(workflow, producer))
      Then("the executable reference is resolved without a content certificate")
      catalog.definition.workflowIdentity.value shouldBe "GoalPhaseWorkflow"
      catalog.definition.workflowRevision.value shouldBe "goal-phase-business-operation-v7"
    }

    "consume a receiver-valid unused state without comparing a compiled copy" in {
      Given("internally consistent executable ABI text with an unconsumed state name")
      val changed = workflow.replace("\"Pending\"", "\"ForeignPending\"")
      When("the owning catalog parses and binds the actual selected executable data")
      val result = take(GoalPhaseRuntimeCatalog.parseC(changed, producer))
      Then("reference resolution succeeds without a complete compiled-state equality gate")
      result.definition.workflowIdentity.value shouldBe "GoalPhaseWorkflow"
    }

    "reject missing or malformed catalog text rather than selecting a fixture fallback" in {
      Given("missing or invalid runtime artifact input")
      When("runtime catalog admission is requested")
      val results = Vector(GoalPhaseRuntimeCatalog.parseC(null, producer),
        GoalPhaseRuntimeCatalog.parseC(workflow, null), GoalPhaseRuntimeCatalog.parseC("{}", producer),
        GoalPhaseRuntimeCatalog.parseC(workflow, "{}"))
      Then("all admissions fail closed without a fallback catalog")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "consume receiver-valid Judgment data without a compiled-policy copy proof" in {
      Given("a structurally valid producer whose Judgment goal text is not compared with a compiled copy")
      val root = io.circe.parser.parse(producer).fold(error => fail(error.toString), identity)
      val changed = root.hcursor.downField("models").downArray.downField("judgments").downArray
        .downField("goal").downField("value").withFocus(_ => io.circe.Json.fromString("current goal description"))
        .top.getOrElse(fail("Judgment goal is absent")).noSpaces
      When("the catalog consumes that actual producer through the common parser and native binder")
      val result = take(GoalPhaseRuntimeCatalog.parseC(workflow, changed))
      Then("the actual receiver-valid producer is not refused merely because compiled producer text differs")
      result.definition.workflowIdentity.value shouldBe "GoalPhaseWorkflow"
    }

    "accept display and source metadata changes without treating them as policy incompatibility" in {
      Given("Spec: docs/spec/workflow-definition-reference.md; Rule: R1; Example: E3; a compatible producer with moved sources")
      val root = io.circe.parser.parse(producer).fold(error => fail(error.toString), identity)
      val model = root.hcursor.downField("models").downArray
      val changes = Vector(
        model.downField("model").downField("compositeStateMachineName").withFocus(_ => io.circe.Json.fromString("foreign-name")),
        model.downField("model").downField("compositeStateMachineSource").downField("line").withFocus(_ => io.circe.Json.fromInt(999)),
        model.downField("judgments").downArray.downField("actionSource").downField("line").withFocus(_ => io.circe.Json.fromInt(999))) ++
        Vector("actionSource", "semanticSource", "candidateActionSource").map(field =>
          model.downField("admissions").downArray.downField(field).downField("line").withFocus(_ => io.circe.Json.fromInt(999))) ++
        Vector("judgmentSource", "admissionSource", "candidateActionSource").map(field =>
          model.downField("judgmentAdmissions").downArray.downField(field).downField("line").withFocus(_ => io.circe.Json.fromInt(999))) ++
        Vector("capabilitySource", "actionSource").map(field =>
          model.downField("requiredSpi").downArray.downField(field).downField("line").withFocus(_ => io.circe.Json.fromInt(999)))
      When("canonical structural admission succeeds for each substitution")
      changes.foreach { cursor =>
        val changed = cursor.top.getOrElse(fail("correlation mutation path missing")).noSpaces
        withClue(changed) {
          take(org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.parseC(changed))
          Then("diagnostic source correlations and display labels are not restart gates")
          GoalPhaseRuntimeCatalog.parseC(workflow, changed) shouldBe a[Consequence.Success[?]]
        }
      }
    }

    "accept formatting and generation metadata changes without content certificates" in {
      Given("a valid executable artifact with different formatting and generator annotation")
      When("the actual catalog resolves the selected reference and consumes its executable fields")
      val spaced = take(GoalPhaseRuntimeCatalog.parseC(workflow + "\n", producer.replace(
        "cozy.modeler.CandidateAdmissionProducerAbiGenerator", "another-compatible-generator")))
      Then("no unchanged-content certificate is produced or required")
      spaced.definition.workflowIdentity.value shouldBe "GoalPhaseWorkflow"
    }
  }
}
