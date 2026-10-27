package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterKey}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  6, 2026
 * @version Oct. 27, 2026
 * @author  ASAMI, Tomoharu
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ThreeWorkflowLauncherAssemblySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val limitKey = ComponentParameterKey.requiredInt("three-workflow.host.limit")
  private val component = SmWorkflowComponent.componentId
  private def creation() = ComponentCreate(RuntimeBindingAdmissionFixture.admit(Subsystem(
    name = "native-three-workflow-host", version = None,
    configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver = AliasResolver.empty, runMode = RunMode.Command)), ComponentOrigin.Builtin,
    Vector(ComponentDescriptor(name = Some(component.name), componentName = Some(component.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(component), config = Map(limitKey.name -> "31"))))

  "Native three-workflow launcher composition" should {
    "publish only actual generated native operation identities rather than guessed skill-stage names" in {
      Given("the five generated CML provided-operation declarations and generated Journal and Evidence services")
      val generated = Vector(domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow,
        domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2.workflow,
        domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3.workflow,
        domain.statemachine.providedapi.StandaloneFixWorkflowStateMachineProvidedApi4.workflow,
        domain.statemachine.providedapi.GoalTaskWorkflowStateMachineProvidedApi5.workflow)
      When("the native launcher publication set is compared with the generated operations")
      val declared = generated.flatMap(_.providedOperations.map(o => s"${o.service}.${o.name}")).toSet ++
        SmWorkflowComponent.JournalServiceService.operations.operations.toVector.map(o => s"${SmWorkflowComponent.JournalServiceService.name}.${o.name}") ++
        SmWorkflowComponent.EvidenceServiceService.operations.operations.toVector.map(o => s"${SmWorkflowComponent.EvidenceServiceService.name}.${o.name}") ++
        SmWorkflowComponent.DevelopmentObservationServiceService.operations.operations.toVector.map(o =>
          s"${SmWorkflowComponent.DevelopmentObservationServiceService.name}.${o.name}")
      val expected = Set(
        "GoalTaskService.startGoalTask", "GoalTaskService.advanceGoalTask",
        "GoalTaskService.submitGoalTaskWorkResult", "GoalTaskService.resolveGoalTaskDiagnosticInformation",
        "GoalTaskService.describeGoalTaskMalformedIssuedWorkRecovery", "GoalTaskService.recoverGoalTaskMalformedIssuedWork",
        "StandaloneFixService.startStandaloneFix", "StandaloneFixService.advanceStandaloneFix",
        "StandaloneFixService.submitStandaloneFixWorkResult", "StandaloneFixService.resolveStandaloneFixDiagnosticInformation",
        "GoalPhaseService.startGoalPhase", "GoalPhaseService.advanceGoalPhase", "GoalPhaseService.submitGoalPhaseWorkResult",
        "GoalPhaseService.submitGoalPhaseOrderedDecision",
        "GoalPhaseService.submitGoalPhaseFindingRecording",
        "GoalPhaseService.describeGoalPhaseValidationRecovery", "GoalPhaseService.recoverGoalPhaseValidation",
        "GoalPhaseService.describeGoalPhaseRemainingPlan", "GoalPhaseService.adoptGoalPhaseRemainingPlan",
        "GoalPhaseService.describeGoalPhaseFailedPhaseRepair", "GoalPhaseService.adoptGoalPhaseFailedPhaseRepair",
        "GoalPhaseService.describeGoalPhaseCompletionPolicy", "GoalPhaseService.adoptGoalPhaseCompletionPolicy",
        "SplitPhaseService.startSplitPhase", "SplitPhaseService.advanceSplitPhase", "SplitPhaseService.submitPartitionSemanticResult",
        "SplitPhaseService.submitHandoffScopeResult", "SplitPhaseService.submitSplitHumanDirection", "SplitPhaseService.submitReasoningResult",
        "RepositorySyncService.startRepositorySync", "RepositorySyncService.advanceRepositorySync",
        "RepositorySyncService.submitRepositorySyncWorkResult", "RepositorySyncService.submitNativeMergeReview",
        "RepositorySyncService.submitRepositoryConflictDecision", "RepositorySyncService.submitNativeMergeRepair",
        "JournalService.readJournalEntry", "EvidenceService.readEvidence", "EvidenceService.recordEvidenceJournal",
        "DevelopmentObservationService.readDevelopmentState", "DevelopmentObservationService.readDevelopmentHistory")
      Then("every published native operation has a generated counterpart and each owning advance has its paired Start")
      expected.subsetOf(ThreeWorkflowLauncherAssembly.publishedMcpOperations) shouldBe true
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("GoalPhaseService.submitGoalPhaseOrderedDecision")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("GoalPhaseService.describeGoalPhaseReleaseRepairContinuation")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("GoalPhaseService.recoverGoalPhaseReleaseRepairContinuation")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations.subsetOf(declared) shouldBe true
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("RepositorySyncService.submitNativeMergeReview")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should not contain "RepositorySyncService.submitMergeReviewResult"
    }

    "bind generated passive preparation signatures without adding semantic MCP publication" in {
      Given("the actual generated preparation declarations and unchanged workflow publication boundary")
      val declared = SmWorkflowComponent.NativeWorkflowPreparationServiceService
      When("the normal preparation service binds its actual generated metadata")
      val bound = NativeWorkflowPreparationServiceAssembly.bind
      Then("every actual operation retains its concrete input and output contract while publication stays workflow owned")
      bound.name shouldBe declared.name
      bound.operations.operations.map(_.name) shouldBe declared.operations.operations.map(_.name)
      bound.operations.operations.toVector.zip(declared.operations.operations.toVector).foreach { (actual, generated) =>
        actual.specification.request shouldBe generated.specification.request
        actual.specification.response shouldBe generated.specification.response
      }
      ThreeWorkflowLauncherAssembly.publishedMcpOperations.exists(_.startsWith(bound.name + ".")) shouldBe false
    }

    "decode trusted host dependencies once only after canonical parameter resolution and refuse absence" in {
      Given("a real canonical creation capsule and a trusted host decoder with no production verifiers installed")
      val create = creation()
      var observed = Vector.empty[(ComponentCreate, ComponentInitializationParameters, ExecutionContext)]
      val decoder = new ThreeWorkflowLauncherAssembly.RuntimeCompositionDecoder {
        def decodeC(c: ComponentCreate, p: ComponentInitializationParameters, context: ExecutionContext) = {
          observed :+= ((c, p, context))
          Consequence.configurationInvalid("test-only independent host evidence is not installed")
        }
      }
      val factory = ThreeWorkflowLauncherAssembly.factory(Vector(limitKey), Vector.empty, decoder).primaryFactory
      When("the canonical public factory attempts to assemble the native component")
      val result = factory.createPrimaryC(create)
      Then("absence is an error, not a legacy service or fabricated evidence fallback")
      result.isSuccess shouldBe false
      observed.map(_._1) shouldBe Vector(create)
      observed.head._2.resolve(limitKey).toOption.get.value shouldBe Some(31)
    }

    "reject an incomplete native host before attempting to bind public services" in {
      Given("an owning creation capsule without the required independent declarations or verifiers")
      val create = creation()
      given ExecutionContext = ExecutionContext.create()
      val incomplete = ThreeWorkflowLauncherAssembly.RuntimeComposition(null, null, null, null, null)
      When("the native bundle binder receives that incomplete host")
      val result = ThreeWorkflowLauncherAssembly.bindC(create, incomplete)
      Then("it fails without manufacturing storage, Human consent, review evidence or process grants")
      result.isSuccess shouldBe false
    }
  }
}
