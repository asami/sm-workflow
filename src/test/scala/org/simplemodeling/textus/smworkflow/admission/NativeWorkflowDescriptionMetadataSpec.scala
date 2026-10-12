package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.value.BaseContent
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Metadata connection mechanics, not proof that the current generator emits
  * nonempty descriptions or that native all-three production hosts are installed.
  * The complete generated Help contract separately requires real descriptions.
  * @since   Oct.  7, 2026
  * @version Oct. 12, 2026
  */
final class NativeWorkflowDescriptionMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _operations: Vector[(String, spec.OperationDefinition)] = Vector(
    "GoalPhaseService" -> SmWorkflowComponent.GoalPhaseServiceService.StartGoalPhaseOperation,
    "GoalPhaseService" -> SmWorkflowComponent.GoalPhaseServiceService.AdvanceGoalPhaseOperation,
    "GoalPhaseService" -> SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation,
    "GoalPhaseService" -> SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation,
    "RepositorySyncService" -> SmWorkflowComponent.RepositorySyncServiceService.StartRepositorySyncOperation,
    "RepositorySyncService" -> SmWorkflowComponent.RepositorySyncServiceService.AdvanceRepositorySyncOperation,
    "RepositorySyncService" -> SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncWorkResultOperation,
    "RepositorySyncService" -> SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation,
    "RepositorySyncService" -> SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation,
    "SplitPhaseService" -> SmWorkflowComponent.SplitPhaseServiceService.StartSplitPhaseOperation,
    "SplitPhaseService" -> SmWorkflowComponent.SplitPhaseServiceService.AdvanceSplitPhaseOperation,
    "SplitPhaseService" -> SmWorkflowComponent.SplitPhaseServiceService.SubmitPartitionSemanticResultOperation,
    "SplitPhaseService" -> SmWorkflowComponent.SplitPhaseServiceService.SubmitHandoffScopeResultOperation,
    "SplitPhaseService" -> SmWorkflowComponent.SplitPhaseServiceService.SubmitReasoningResultOperation,
    "SplitPhaseService" -> SmWorkflowComponent.SplitPhaseServiceService.SubmitSplitHumanDirectionOperation
  )
  private val _services: Vector[spec.ServiceDefinition] = Vector(
    SmWorkflowComponent.GoalPhaseServiceService,
    SmWorkflowComponent.RepositorySyncServiceService,
    SmWorkflowComponent.SplitPhaseServiceService
  )

  "Native workflow descriptions" should {
    "normal named operation specifications" which {
      _operations.foreach { case (service, declared) =>
        s"retain $service.${declared.name}'s named request, response and generated descriptions" in {
          Given("the actual generated CML Operation and its named business contract")
          val generated = declared.specification

          When("the normal native binder borrows that declaration's metadata")
          val actual = NativeWorkflowDescriptionMetadata.operation(declared)

          Then("the actual named input/output contract remains attached to the operation")
          actual.name shouldBe declared.name
          actual.content.nameAttributes shouldBe BaseContent.simple(declared.name).nameAttributes
          actual.content.descriptiveAttributes shouldBe generated.content.descriptiveAttributes
          actual.summary shouldBe generated.summary
          actual.description shouldBe generated.description
          actual.request should be theSameInstanceAs generated.request
          actual.response should be theSameInstanceAs generated.response
          actual.request.parameters.map(_.name) should not contain ("arg1")
          actual.request.parameters.map(_.name) should contain ("sessionId")
          actual.response should not be spec.ResponseDefinition.void
        }
      }
    }

    "native service specifications" which {
      _services.foreach { declared =>
        s"connect ${declared.name}'s description without installing any additional declared operation" in {
          Given("a generated service catalog and one existing native operation object")
          var decoded = Vector.empty[Request]
          val native = new spec.OperationDefinition {
            val specification = NativeWorkflowDescriptionMetadata.operation(declared.operations.head)
            def createOperationRequest(request: Request): Consequence[OperationRequest] = {
              decoded :+= request
              Consequence.stateConflict("test-only existing native decoder refusal")
            }
          }
          val operations = spec.OperationDefinitionGroup(native)
          val request = Request(component = None, service = Some(declared.name),
            operation = native.name, arguments = Nil, switches = Nil, properties = Nil)

          When("the service metadata is assembled around that actual operation and the request reaches its decoder")
          val actual = NativeWorkflowDescriptionMetadata.service(declared, operations)
          val decodedResult = actual.operations.head.createOperationRequest(request)

          Then("the service contains the exact existing operation, not generated handlers or a metadata wrapper action")
          actual.name shouldBe declared.name
          actual.specification.content.nameAttributes shouldBe BaseContent.simple(declared.name).nameAttributes
          actual.specification.content.descriptiveAttributes shouldBe declared.specification.content.descriptiveAttributes
          actual.specification.description shouldBe declared.specification.description
          actual.operations should be theSameInstanceAs operations
          actual.operations.operations.toVector.size shouldBe 1
          actual.operations.head should be theSameInstanceAs native
          actual.useDefault shouldBe false
          actual.metadata shouldBe spec.ServiceMetadata.empty
          decoded shouldBe Vector(request)
          decodedResult.isSuccess shouldBe false
        }
      }
    }
  }
}
