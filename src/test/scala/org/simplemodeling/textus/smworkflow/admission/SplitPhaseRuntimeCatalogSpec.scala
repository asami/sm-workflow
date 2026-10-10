package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
final class SplitPhaseRuntimeCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"catalog admission failed: $other")
  }
  private def workflowText = Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json"))
  private def producerText = Files.readString(Path.of("target/cozy/candidate-admission-producer-abi.json"))

  "SplitPhase official runtime catalog" should {
    "admit the actual component JAR without filtering or rewriting its multi-Workflow resources" in {
      Given("the actual packageBin JAR and independently readable official generated ABI files")
      val jar = Path.of("target/scala-3.3.8/textus-sm-workflow_3-0.1.0-SNAPSHOT.jar")
      withClue("Run packageBin before catalog artifact acceptance: ")(Files.isRegularFile(jar) shouldBe true)
      val loader = new URLClassLoader(Array(jar.toUri.toURL), null)
      try {
        When("the packaged SplitPhase definition is selected against all compiled policy facts")
        val catalog = take(SplitPhaseRuntimeCatalog.loadC(loader))
        Then("the complete official resource bytes and exact SplitPhase binding are retained")
        catalog.workflow.workflow shouldBe take(SplitPhaseDefinitionBinding.candidateC).workflow
        catalog.definition.workflowIdentity.value shouldBe "SplitPhaseWorkflow"
        catalog.definition.workflowRevision.value shouldBe "split-phase-selected-application-v12"
        catalog.definition.validateC shouldBe a[Consequence.Success[?]]
        catalog.producer.models.map(_.model.workflow.map(_.identity.value)).flatten.toSet shouldBe
          Set("GoalPhaseWorkflow", "SplitPhaseWorkflow", "RepositorySyncWorkflow")
        take(SplitPhaseRuntimeCatalog.loadC()).workflow.workflow.identity shouldBe "SplitPhaseWorkflow"
      } finally loader.close()
    }

    "refuse an independently admitted old definition without converting it to the current version" in {
      Given("complete generated Workflow and producer data with mutually consistent historical v4 revisions")
      val workflow = parse(workflowText).fold(e => fail(e.toString), identity)
      val producer = parse(producerText).fold(e => fail(e.toString), identity)
      val wi = workflow.hcursor.downField("workflows").focus.get.asArray.get.indexWhere(
        _.hcursor.get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
      val pi = producer.hcursor.downField("models").focus.get.asArray.get.indexWhere(
        _.hcursor.downField("model").downField("workflow").get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
      wi should be >= 0
      pi should be >= 0
      val revision = Json.fromString("split-phase-issued-reply-v4")
      val oldWorkflow = workflow.hcursor.downField("workflows").downN(wi).downField("version")
        .withFocus(_ => revision).top.get.noSpaces
      val oldProducer = producer.hcursor.downField("models").downN(pi).downField("model")
        .downField("workflow").downField("version").withFocus(_ => revision).top.get.noSpaces
      val admitted = take(CandidateAdmissionProducerAbi.parseC(oldProducer))
      val candidate = take(CandidateWorkflowAbi.parseSelectedC(oldWorkflow, admitted, "src/main/cozy/sm-workflow.cml",
        "SplitPhaseWorkflow", "split-phase-issued-reply-v4"))
      val oldBinding = take(org.goldenport.cncf.workflow.WorkflowInstancePersistence.bindCandidateDefinitionC(candidate))
      When("the valid old binding and resources encounter the current compiled catalog")
      val current = take(SplitPhaseDefinitionBinding.bindC)
      val result = SplitPhaseRuntimeCatalog.parseC(oldWorkflow, oldProducer)
      Then("v4 remains unchanged incompatible data rather than an implicitly migrated v6 definition")
      oldBinding.workflowRevision.value shouldBe "split-phase-issued-reply-v4"
      current.workflowRevision.value shouldBe "split-phase-selected-application-v12"
      SplitPhaseDefinitionBinding.sameDefinition(oldBinding, current) shouldBe false
      result shouldBe a[Consequence.Failure[?]]
    }

    "reject absent duplicate and missing runtime owners without checked-in fixture fallback" in {
      Given("empty and duplicate official resource classloaders")
      val location = SplitPhaseRuntimeCatalog.getClass.getProtectionDomain.getCodeSource.getLocation
      val empty = new URLClassLoader(Array.empty[java.net.URL], null)
      val parent = new URLClassLoader(Array(location), null)
      val duplicate = new URLClassLoader(Array(location), parent)
      try {
        When("catalog admission is attempted with each incompatible owner")
        val results = Vector(SplitPhaseRuntimeCatalog.loadC(empty), SplitPhaseRuntimeCatalog.loadC(duplicate),
          SplitPhaseRuntimeCatalog.loadC(null), SplitPhaseRuntimeCatalog.parseC(null, producerText),
          SplitPhaseRuntimeCatalog.parseC(workflowText, null), SplitPhaseRuntimeCatalog.parseC("{}", producerText),
          SplitPhaseRuntimeCatalog.parseC(workflowText, "{}"))
        Then("no usable catalog or persistence binding is produced")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      } finally {
        duplicate.close()
        parent.close()
        empty.close()
      }
    }

    "reject a structurally admitted state substitution against the compiled SplitPhase Workflow" in {
      Given("the complete official Workflow ABI with one SplitPhase state renamed")
      val original = parse(workflowText).fold(error => fail(error.toString), identity)
      val workflows = original.hcursor.downField("workflows").focus.flatMap(_.asArray).get
      val index = workflows.indexWhere(_.hcursor.get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
      index should be >= 0
      val changed = original.hcursor.downField("workflows").downN(index).downField("states").downArray
        .downField("name").withFocus(_ => Json.fromString("ForeignEntry")).top.get.noSpaces
      val producer = take(CandidateAdmissionProducerAbi.parseC(producerText))
      take(CandidateWorkflowAbi.parseSelectedC(changed, producer, "src/main/cozy/sm-workflow.cml",
        "SplitPhaseWorkflow", "split-phase-selected-application-v12"))
      When("compiled runtime admission receives the structurally valid foreign state")
      val result = SplitPhaseRuntimeCatalog.parseC(changed, producerText)
      Then("the state substitution cannot become an admitted runtime catalog")
      result shouldBe a[Consequence.Failure[?]]
    }

    "reject receiver-valid semantic-policy substitutions without weakening deterministic estimate ownership" in {
      Given("the official producer with individually changed SplitPhase judgment facts")
      val original = parse(producerText).fold(error => fail(error.toString), identity)
      val models = original.hcursor.downField("models").focus.flatMap(_.asArray).get
      val index = models.indexWhere(_.hcursor.downField("model").downField("workflow")
        .get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
      index should be >= 0
      val judgment = original.hcursor.downField("models").downN(index).downField("judgments").downArray
      val fields = Vector("goal", "context", "candidate", "expectedResult", "rationale", "evidence",
        "evidenceScope", "evidenceFreshness", "evidenceProvenance")
      val changes = fields.map(field =>
        judgment.downField(field).downField("value").withFocus(_ => Json.fromString("foreign-policy"))) ++
        Vector("alternatives", "criteria").map(field =>
          judgment.downField(field).downArray.downField("value").withFocus(_ => Json.fromString("foreign-policy")))
      When("each receiver-valid changed policy is compared with the complete compiled producer")
      val results = changes.map { cursor =>
        val changed = cursor.top.getOrElse(fail("missing policy mutation path")).noSpaces
        val producer = take(CandidateAdmissionProducerAbi.parseC(changed))
        take(CandidateWorkflowAbi.parseSelectedC(workflowText, producer, "src/main/cozy/sm-workflow.cml",
          "SplitPhaseWorkflow", "split-phase-selected-application-v12"))
        SplitPhaseRuntimeCatalog.parseC(workflowText, changed)
      }
      Then("none can replace unresolved-input-only judgment or compiled evidence policy")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
