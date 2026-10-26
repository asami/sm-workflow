package org.simplemodeling.textus.smworkflow

import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.cli.renderer.{CliHelpJsonRenderer, CliHelpYamlRenderer}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.projection.HelpProjection
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.spec.ServiceDefinition
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct. 26, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Generated declaration Help, not the installed production launcher profile.
 * The controlled test owner binds the actual generated factory and definitions;
 * no replacement metadata, operation execution, storage or command grants are used.
 * A description emitted only as a Scala comment does not satisfy this contract.
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SmWorkflowGeneratedHelpSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _declarations: Vector[(ServiceDefinition, Vector[String])] = Vector(
    SmWorkflowComponent.NativeWorkflowPreparationServiceService -> Vector(
      "prepareGoalStart", "prepareRepositoryStart", "prepareSplitStart", "prepareRepositoryEffect", "prepareAuthority", "prepareEvidence",
      "prepareExecution", "recordExecution", "prepareCallerInformation", "recordCallerInformation"
    ),
    SmWorkflowComponent.GoalPhaseServiceService -> Vector(
      "startGoalPhase", "advanceGoalPhase", "submitGoalPhaseWorkResult", "submitGoalPhaseOrderedDecision", "submitGoalPhaseFindingRecording",
      "resolveGoalPhaseDiagnosticInformation",
      "describeGoalPhaseValidationRecovery", "recoverGoalPhaseValidation",
      "describeGoalPhaseClosureRecovery", "recoverGoalPhaseClosure",
      "describeGoalPhaseSourceHistoryRecovery", "recoverGoalPhaseSourceHistory",
      "describeGoalPhaseSourceHistoryNormalization", "adoptGoalPhaseSourceHistoryNormalization",
      "describeGoalPhaseCandidateRecovery", "recoverGoalPhaseCandidate",
      "describeGoalPhaseWholePhaseCandidateRecovery", "recoverGoalPhaseWholePhaseCandidate",
      "describeGoalPhaseCandidateObservationContinuation", "continueGoalPhaseCandidateObservation",
      "describeGoalPhaseRemainingPlan", "adoptGoalPhaseRemainingPlan",
      "describeGoalPhaseReleaseRepairContinuation", "recoverGoalPhaseReleaseRepairContinuation",
      "describeGoalPhaseReviewPurposeRepair", "admitGoalPhaseReviewPurposeRepair",
      "describeGoalPhaseCompletionPolicy", "adoptGoalPhaseCompletionPolicy",
      "describeGoalPhaseRepairPolicy", "adoptGoalPhaseRepairPolicy",
      "describeGoalPhaseFailedPhaseRepair", "adoptGoalPhaseFailedPhaseRepair",
      "describeGoalPhaseValidationRepair", "adoptGoalPhaseValidationRepair"
    ),
    SmWorkflowComponent.SplitPhaseServiceService -> Vector(
      "startSplitPhase", "advanceSplitPhase", "submitPartitionSemanticResult",
      "submitHandoffScopeResult", "submitReasoningResult", "submitSplitHumanDirection"
    ),
    SmWorkflowComponent.RepositorySyncServiceService -> Vector(
      "startRepositorySync", "advanceRepositorySync", "submitRepositorySyncWorkResult",
      "submitNativeMergeReview", "submitNativeMergeRepair", "submitRepositoryConflictDecision"
    ),
    SmWorkflowComponent.DevelopmentObservationServiceService -> Vector("readDevelopmentState", "readDevelopmentHistory"),
    SmWorkflowComponent.JournalServiceService -> Vector("readJournalEntry", "findJournalEntries"),
    SmWorkflowComponent.EvidenceServiceService -> Vector("readEvidence", "recordEvidenceJournal",
      "archiveEvidenceBatch", "describeEvidence", "manageEvidenceRetention", "findDueEvidence"),
    SmWorkflowComponent.RepositoryHumanServiceService -> Vector("presentRepositoryConflictDecision"),
    SmWorkflowComponent.RepositoryConflictServiceService -> Vector("resolveRepositoryMergeConflicts"),
    SmWorkflowComponent.GoalPhaseOrderedHumanServiceService -> Vector("presentOrderedGoalDecision"),
    SmWorkflowComponent.GoalPhaseImplementationServiceService -> Vector("implementGoalPhaseStep"),
    SmWorkflowComponent.GoalPhaseRepairWorkServiceService -> Vector("repairGoalPhaseStep"),
    SmWorkflowComponent.GoalPhaseStepReviewServiceService -> Vector("reviewGoalPhaseStep"),
    SmWorkflowComponent.GoalPhaseStepTriageServiceService -> Vector("triageGoalPhaseStep"),
    SmWorkflowComponent.GoalPhaseReviewServiceService -> Vector("reviewGoalPhase"),
    SmWorkflowComponent.GoalPhaseFindingRecordingServiceService -> Vector("recordGoalFindings"),
    SmWorkflowComponent.MergeReviewServiceService -> Vector("reviewNativePendingMerge"),
    SmWorkflowComponent.MergeRepairServiceService -> Vector("repairNativePendingMerge"),
    SmWorkflowComponent.SplitSemanticServiceService -> Vector(
      "assessPartitionSemanticInputs", "assessHandoffScopes", "assessReasoningDemand"
    ),
    SmWorkflowComponent.SplitSemanticAdmissionServiceService -> Vector(
      "admitPartitionSemanticInputs", "admitHandoffScopes", "admitReasoningDemand"
    ),
    SmWorkflowComponent.SplitHumanServiceService -> Vector("presentSplitHumanDirection", "presentReasoningHumanDirection"),
    SmWorkflowComponent.RepositorySyncSemanticAdmissionServiceService -> Vector("admitRepositoryConflicts", "admitNativeMergeReview", "admitNativeMergeRepair"),
    SmWorkflowComponent.GoalPhaseSemanticAdmissionServiceService -> Vector("admitOrderedStepReview")
  )
  // The generated Component retains CNCF's canonical host services in addition
  // to the complete CML catalog. They are not invented workflow capabilities.
  private val _host_service_names = Vector("meta", "system")

  private val _limits = Vector(
    ("GoalPhaseService", "submitGoalPhaseOrderedDecision", Vector(
      "Independent host Human evidence", "authentication or AI output is not consent"
    )),
    ("SplitPhaseService", "advanceSplitPhase", Vector(
      "authorized document effects", "Unavailable handling is an error"
    )),
    ("RepositorySyncService", "advanceRepositorySync", Vector(
      "The caller sends no stage/path/argv/receipt/target selector", "no replay/intermediate recovery is promised"
    )),
    ("RepositoryHumanService", "presentRepositoryConflictDecision", Vector(
      "independent Human channel", "infer consent from login identity"
    )),
    ("RepositoryConflictService", "resolveRepositoryMergeConflicts", Vector(
      "Do not modify files", "grants command authority"
    )),
    ("MergeRepairService", "repairNativePendingMerge", Vector(
      "exactly the assigned finding/file scope", "subsequent reviewer remains independent"
    )),
    ("RepositorySyncService", "submitNativeMergeRepair", Vector(
      "Submit never calls Advance", "Decision-needed stays nonterminal"
    )),
    ("GoalPhaseImplementationService", "implementGoalPhaseStep", Vector(
      "do not select another Step", "supply native receipts"
    )),
    ("GoalPhaseRepairWorkService", "repairGoalPhaseStep", Vector(
      "Do not expand the repair scope", "reset counters"
    )),
    ("GoalPhaseStepReviewService", "reviewGoalPhaseStep", Vector(
      "Do not repair the candidate", "a passing subset is not complete candidate acceptance"
    )),
    ("GoalPhaseReviewService", "reviewGoalPhase", Vector(
      "whole selected Phase", "assert Phase completion from partial success"
    ))
  )

  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Generated declaration assembly failed: $other")
  }

  private def _component(): Component = {
    val owner = RuntimeBindingAdmissionFixture.admit(Subsystem(
      name = "sm-workflow-generated-help-spec",
      version = None,
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command
    ))
    val factory = new SmWorkflowComponent.Factory()
    _take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
  }

  private def _selector(service: String, operation: String): String =
    s"${SmWorkflowComponent.componentId.name}.$service.$operation"

  "SmWorkflow generated Help" must afterWord("in spec:repository-sync-native-merge-repair, example:C1, rules:R5,R6") {
    "normal passive execution descriptions" which {
      Vector("prepareExecution" -> "workflowcallerexecutionprepareinput", "recordExecution" -> "workflowcallerexecutionrecordinput")
        .foreach { (name, inputtype) =>
        s"describe the concrete input and execution-state output of $name" in {
          Given("spec:native-host-execution R6,R7,R9: the generated passive execution declaration " + name)
          val operation = SmWorkflowComponent.NativeWorkflowPreparationServiceService.operations.operations.find(_.name == name).get
          When("ordinary canonical Help projects that exact generated Operation")
          val help = HelpProjection.projectModel(_component(), Some(_selector("NativeWorkflowPreparationService", name)))
          Then("the one named input, concrete public type and opaque execution-state output remain ordinary generated Help metadata")
          help.details("arguments") shouldBe Vector("input")
          help.details("returns").mkString should include("WorkflowCallerExecutionState")
          help.details("description").mkString should include("opaque")
          help.details("description").mkString should include("No provider IO")
          operation.specification.request.parameters.head.name shouldBe "input"
          operation.specification.request.parameters.head.datatype.name should include(inputtype)
          help.details("argumentDetails").mkString should include(inputtype)
        }
      }
    }

    "the declared component catalog" which {
      "retain the complete declared operation catalog alongside the canonical host services" in {
        Given("the complete public declaration catalog, independently of production handler installation")
        val names = _declarations.map(_._1.name).sorted

        When("the generated component declaration is assembled and its canonical Help selector is interpreted")
        val component = _component()
        val help = HelpProjection.projectModel(component, Some(SmWorkflowComponent.componentId.name))
        val services = component.protocol.services.services.toVector
        val declaredServices = services.filter(service => names.contains(service.name))
        val hostServices = services.filterNot(service => names.contains(service.name))
        val operations = declaredServices.flatMap(_.operations.operations.toVector)

        Then("Help resolves the declared component without omitting a service or substituting a production profile")
        help.`type` shouldBe "component"
        help.componentId shouldBe Some(SmWorkflowComponent.componentId.name)
        help.selector.map(_.canonical) shouldBe Some(SmWorkflowComponent.componentId.name)
        names.distinct.size shouldBe names.size
        declaredServices.map(_.name).sorted shouldBe names
        hostServices.map(_.name).sorted shouldBe _host_service_names.sorted
        help.children shouldBe (names ++ _host_service_names).sorted
        help.children.size shouldBe names.size + _host_service_names.size
        help.details("services") shouldBe help.children
        operations.size shouldBe _declarations.flatMap(_._2).size
        declaredServices.foreach { service =>
          service.operations.operations.map(_.name).toVector.sorted shouldBe
            _declarations.find(_._1.name == service.name).get._2.sorted
        }
      }
    }

    "service descriptions" which {
      _declarations.foreach { case (service, names) =>
        s"expose ${service.name}'s generated description and original operation catalog" in {
          Given(s"the CML-described ${service.name} declaration and its assigned operation identities")
          val selector = s"${SmWorkflowComponent.componentId.name}.${service.name}"

          When("the actual generated service metadata is projected through canonical component Help")
          val component = _component()
          val help = HelpProjection.projectModel(component, Some(selector))
          val description = service.specification.description.map(_.displayMessage.trim).filter(_.nonEmpty)
          val summary = service.specification.summary.map(_.displayMessage.trim).filter(_.nonEmpty)

          Then("a Scala comment alone cannot replace a description carried by the generated service specification")
          description should not be empty
          help.`type` shouldBe "service"
          help.name shouldBe service.name
          help.componentId shouldBe Some(SmWorkflowComponent.componentId.name)
          help.selector.map(_.canonical) shouldBe Some(selector)
          help.summary shouldBe summary.orElse(description).getOrElse("")
          help.children shouldBe names.sorted
          help.details("operations") shouldBe names.sorted
        }
      }
    }

    "operation descriptions" which {
      _declarations.foreach { case (service, names) =>
        names.foreach { name =>
          s"preserve ${service.name}.$name's description in projected and JSON Help" in {
            Given(s"the generated ${service.name}.$name operation declaration, without an executed action")
            val operation = service.operations.operations.find(_.name == name).getOrElse(
              fail(s"Generated operation declaration is missing: ${service.name}.$name")
            )
            val selector = _selector(service.name, name)

            When("the canonical operation selector is projected and rendered by the actual CLI JSON renderer")
            val component = _component()
            val help = HelpProjection.projectModel(component, Some(selector))
            val description = operation.specification.description.map(_.displayMessage.trim).filter(_.nonEmpty)
            val rendered = parse(CliHelpJsonRenderer.render(help)).fold(
              error => fail(s"Operation Help is not JSON: $error"), identity
            )

            Then("the generated description remains Help metadata and survives JSON encoding without loss")
            description should not be empty
            help.`type` shouldBe "operation"
            help.name shouldBe name
            help.service shouldBe Some(service.name)
            help.componentId shouldBe Some(SmWorkflowComponent.componentId.name)
            help.selector.map(_.canonical) shouldBe Some(selector)
            help.details.get("description") shouldBe description.map(Vector(_))
            help.details("arguments") shouldBe operation.specification.request.parameters.map(_.name).toVector
            help.details("returns") should not be empty
            rendered.hcursor.downField("details").get[Vector[String]]("description") shouldBe
              Right(description.toVector)
            rendered.hcursor.downField("selector").get[String]("canonical") shouldBe Right(selector)
          }
        }
      }
    }

    "availability and authority descriptions" which {
      _limits.foreach { case (service, operation, phrases) =>
        s"retain the declared limits of $service.$operation" in {
          Given(s"the documented current availability and authority boundary for $service.$operation")
          val selector = _selector(service, operation)

          When("the actual generated operation description is interpreted as Help")
          val component = _component()
          val help = HelpProjection.projectModel(component, Some(selector))
          val description = help.details.getOrElse("description", Vector.empty).mkString

          Then("Help does not turn a declaration, acknowledgement or fixture into installed execution authority")
          help.`type` shouldBe "operation"
          phrases.foreach { phrase => description should include(phrase) }
        }
      }
    }

    "CLI YAML text" which {
      Vector(
        "GoalPhaseService" -> "submitGoalPhaseOrderedDecision",
        "SplitPhaseService" -> "advanceSplitPhase",
        "RepositorySyncService" -> "advanceRepositorySync"
      ).foreach { case (service, operation) =>
        s"retain $service.$operation's projected description and canonical identity" in {
          Given(s"the generated $service.$operation Help declaration; YAML syntax validation is separate")
          val selector = _selector(service, operation)

          When("canonical Help is projected and rendered by the CLI YAML text renderer")
          val component = _component()
          val help = HelpProjection.projectModel(component, Some(selector))
          val rendered = CliHelpYamlRenderer.render(help)

          Then("the rendered text includes the real projected description, not a fabricated metadata fixture")
          help.`type` shouldBe "operation"
          val description = help.details.getOrElse("description", Vector.empty)
          description should have size 1
          rendered should include(s"canonical: $selector")
          rendered should include("description:")
          description.foreach { text => rendered should include(text) }
        }
      }
    }

    "unknown selectors" which {
      Vector(
        s"${SmWorkflowComponent.componentId.name}.UndeclaredService",
        _selector("SplitPhaseService", "undeclaredOperation")
      ).foreach { selector =>
        s"reject $selector without inventing a declared capability" in {
          Given("a selector for a service or operation absent from the generated declaration catalog")

          When("the absent selector is interpreted against the real generated component")
          val component = _component()
          val help = HelpProjection.projectModel(component, Some(selector))

          Then("Help reports a missing target instead of a successful operation or implied installation")
          help.`type` shouldBe "error"
          help.summary shouldBe "target not found"
          help.selector shouldBe None
          help.children shouldBe Vector.empty
        }
      }
    }
  }
}
