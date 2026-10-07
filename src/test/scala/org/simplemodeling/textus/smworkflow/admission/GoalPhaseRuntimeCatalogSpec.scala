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
        Then("all three official definitions remain in the raw bytes and persistence binding")
        val official = Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json"), StandardCharsets.UTF_8)
        catalog.workflow.rawJson shouldBe official
        take(GoalPhaseRuntimeCatalog.loadC()).workflow.rawJson shouldBe official
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

    "admit exact compiled metadata while preserving raw definition identity" in {
      Given("generated ABI text matching the compiled main descriptor")
      When("the pure runtime catalog parses the text")
      val catalog = take(GoalPhaseRuntimeCatalog.parseC(workflow, producer))
      Then("the original bytes and receiver fingerprint are retained without normalization")
      catalog.workflow.rawJson shouldBe workflow
      catalog.definition.fixtureSha256.value shouldBe catalog.workflow.sha256
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
      val changes = scalarFields.flatMap(field => Vector(
        judgment.downField(field).downField("value").withFocus(_ => io.circe.Json.fromString("foreign-policy")),
        judgment.downField(field).downField("source").downField("line").withFocus(_ => io.circe.Json.fromInt(999)))) ++
        Vector("alternatives", "criteria").flatMap(field => Vector(
          judgment.downField(field).downArray.downField("value").withFocus(_ => io.circe.Json.fromString("foreign-policy")),
          judgment.downField(field).downArray.downField("source").downField("line").withFocus(_ => io.circe.Json.fromInt(999)))) ++
        Vector("semanticSource").map(field =>
          judgment.downField(field).downField("line").withFocus(_ => io.circe.Json.fromInt(999)))
      When("each structurally valid artifact reaches compiled policy admission")
      changes.foreach { cursor =>
        val changed = cursor.top.getOrElse(fail("policy mutation path missing")).noSpaces
        withClue(changed) {
          val admitted = take(org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.parseC(changed))
          org.goldenport.cncf.workflow.CandidateWorkflowAbi.parseC(workflow, admitted,
            "src/main/cozy/sm-workflow.cml") shouldBe a[Consequence.Success[?]]
          Then("the unchanged Workflow contract cannot hide a changed policy or its provenance")
          GoalPhaseRuntimeCatalog.parseC(workflow, changed) shouldBe a[Consequence.Failure[?]]
        }
      }
    }

    "reject receiver-valid model and Admission correlation provenance substitutions" in {
      Given("an admitted producer with individually substituted identity labels or source correlations")
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
          Then("compiled GoalPhase correlation admission rejects the different producer")
          GoalPhaseRuntimeCatalog.parseC(workflow, changed) shouldBe a[Consequence.Failure[?]]
        }
      }
    }

    "preserve whitespace-sensitive receiver identity rather than rewriting artifact bytes" in {
      Given("two semantically equal artifacts with distinct raw bytes")
      When("each is independently admitted against the same compiled descriptor")
      val original = take(GoalPhaseRuntimeCatalog.parseC(workflow, producer))
      val spaced = take(GoalPhaseRuntimeCatalog.parseC(workflow + "\n", producer))
      Then("catalog admission retains each exact fingerprint for persisted binding checks")
      spaced.workflow.workflow shouldBe original.workflow.workflow
      spaced.workflow.rawJson shouldBe workflow + "\n"
      spaced.definition.fixtureSha256 should not be original.definition.fixtureSha256
      GoalPhaseRuntimeCatalog.admitBindingC(null, null, original.producer) shouldBe a[Consequence.Failure[?]]
    }
  }
}
