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
 * @version Oct.  8, 2026
 */
final class GoalPhaseRuntimeCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }
  private val workflow = resource("/workflow/goal-phase-workflow-abi.json")
  private val producer = resource("/workflow/goal-phase-candidate-admission-producer-abi.json")
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
      catalog.definition.workflowRevision.value shouldBe "goal-phase-ordered-run-v4"
    }

    "reject a receiver-valid state substitution against compiled Workflow metadata" in {
      Given("internally consistent ABI text with a foreign Workflow state")
      val changed = workflow.replace("\"Pending\"", "\"ForeignPending\"")
      val parsedProducer = take(org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.parseC(producer))
      org.goldenport.cncf.workflow.CandidateWorkflowAbi.parseC(changed, parsedProducer,
        "src/main/cozy/sm-workflow.cml") shouldBe a[Consequence.Success[?]]
      When("the runtime catalog compares it with compiled metadata")
      val result = GoalPhaseRuntimeCatalog.parseC(changed, producer)
      Then("the foreign definition cannot enter the managed Factory")
      result shouldBe a[Consequence.Failure[?]]
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

    "reject receiver-valid Judgment policy substitutions against the complete compiled producer" in {
      Given("an admitted artifact with individually changed nonblank Judgment facts")
      val root = io.circe.parser.parse(producer).fold(error => fail(error.toString), identity)
      val judgment = root.hcursor.downField("models").downArray.downField("judgments").downArray
      val scalarFields = Vector("goal", "context", "candidate", "expectedResult", "rationale", "evidence",
        "evidenceScope", "evidenceFreshness", "evidenceProvenance")
      val changes = scalarFields.map(field =>
        judgment.downField(field).downField("value").withFocus(_ => io.circe.Json.fromString("foreign-policy"))) ++
        Vector("alternatives", "criteria").map(field =>
          judgment.downField(field).downArray.downField("value").withFocus(_ => io.circe.Json.fromString("foreign-policy")))
      When("each structurally valid artifact reaches compiled policy admission")
      changes.foreach { cursor =>
        val changed = cursor.top.getOrElse(fail("policy mutation path missing")).noSpaces
        withClue(changed) {
          val admitted = take(org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.parseC(changed))
          org.goldenport.cncf.workflow.CandidateWorkflowAbi.parseC(workflow, admitted,
            "src/main/cozy/sm-workflow.cml") shouldBe a[Consequence.Success[?]]
          Then("an incompatible executable judgment policy is rejected")
          GoalPhaseRuntimeCatalog.parseC(workflow, changed) shouldBe a[Consequence.Failure[?]]
        }
      }
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
      Given("two semantically equal artifacts with distinct raw bytes")
      When("each is independently admitted against the same compiled descriptor")
      val original = take(GoalPhaseRuntimeCatalog.parseC(workflow, producer))
      val spaced = take(GoalPhaseRuntimeCatalog.parseC(workflow + "\n", producer.replace(
        "cozy.modeler.CandidateAdmissionProducerAbiGenerator", "another-compatible-generator")))
      Then("no unchanged-content certificate is produced or required")
      spaced.workflow.workflow shouldBe original.workflow.workflow
      SplitPhaseDefinitionBinding.sameDefinition(spaced.definition, original.definition) shouldBe true
      GoalPhaseRuntimeCatalog.admitBindingC(null, null, original.producer) shouldBe a[Consequence.Failure[?]]
    }
  }
}
