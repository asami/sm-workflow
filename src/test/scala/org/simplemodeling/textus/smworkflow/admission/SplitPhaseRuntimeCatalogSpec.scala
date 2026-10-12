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
  * @version Oct. 12, 2026
  */
final class SplitPhaseRuntimeCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"catalog admission failed: $other")
  }
  private def workflowText = Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json"))
  private def producerText = Files.readString(Path.of("target/cozy/candidate-admission-producer-abi.json"))

  "SplitPhase official runtime catalog" should {
    "resolve the actual component owner's multi-Workflow resources" in {
      Given("official resources belonging to the current component runtime owner")
      val location = SplitPhaseRuntimeCatalog.getClass.getProtectionDomain.getCodeSource.getLocation
      val loader = new URLClassLoader(Array(location), null)
      try {
        When("the declared SplitPhase execution reference is resolved")
        val catalog = take(SplitPhaseRuntimeCatalog.loadC(loader))
        Then("the selected definition is bound without requiring a compiled policy copy")
        catalog.workflow.workflow.identity shouldBe "SplitPhaseWorkflow"
        catalog.definition.workflowIdentity.value shouldBe "SplitPhaseWorkflow"
        catalog.definition.workflowRevision.value shouldBe "split-phase-minimal-operation-v13"
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
      When("the valid historical resources do not provide the selected current execution revision")
      val current = take(SplitPhaseDefinitionBinding.bindC)
      val result = SplitPhaseRuntimeCatalog.parseC(oldWorkflow, oldProducer)
      Then("the missing selected revision is refused without converting historical data")
      oldBinding.workflowRevision.value shouldBe "split-phase-issued-reply-v4"
      current.workflowRevision.value shouldBe "split-phase-minimal-operation-v13"
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

    "accept an unused receiver-valid state without a compiled-state copy comparison" in {
      Given("the official Workflow ABI with an additional unused SplitPhase state")
      val original = parse(workflowText).fold(error => fail(error.toString), identity)
      val workflows = original.hcursor.downField("workflows").focus.flatMap(_.asArray).get
      val index = workflows.indexWhere(_.hcursor.get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
      index should be >= 0
      val states = workflows(index).hcursor.downField("states").focus.flatMap(_.asArray).get
      val unused = states.head.mapObject(_.add("name", Json.fromString("UnusedEntry")))
      val changed = original.hcursor.downField("workflows").downN(index).downField("states")
        .withFocus(_ => Json.fromValues(states :+ unused)).top.get.noSpaces
      val producer = take(CandidateAdmissionProducerAbi.parseC(producerText))
      take(CandidateWorkflowAbi.parseSelectedC(changed, producer, "src/main/cozy/sm-workflow.cml",
        "SplitPhaseWorkflow", "split-phase-minimal-operation-v13"))
      When("reference resolution and the common binder consume the valid declaration")
      val result = take(SplitPhaseRuntimeCatalog.parseC(changed, producerText))
      Then("unused state metadata is not a reason to refuse a usable definition")
      result.definition.workflowIdentity.value shouldBe "SplitPhaseWorkflow"
    }

    "accept unused receiver-valid judgment metadata without a compiled-policy comparison" in {
      Given("the official producer with a changed judgment description")
      val original = parse(producerText).fold(error => fail(error.toString), identity)
      val models = original.hcursor.downField("models").focus.flatMap(_.asArray).get
      val index = models.indexWhere(_.hcursor.downField("model").downField("workflow")
        .get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
      index should be >= 0
      val judgment = original.hcursor.downField("models").downN(index).downField("judgments").downArray
      val changed = judgment.downField("context").downField("value")
        .withFocus(_ => Json.fromString("updated-description")).top.get.noSpaces
      When("the selected executable declaration is resolved from this receiver-valid producer")
      val result = take(SplitPhaseRuntimeCatalog.parseC(workflowText, changed))
      Then("unused descriptive metadata does not gate execution")
      result.definition.workflowIdentity.value shouldBe "SplitPhaseWorkflow"
    }
  }
}
