package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.schema.Multiplicity
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
  private val _typed_response_operations: Vector[spec.OperationDefinition] = Vector(
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncWorkResultOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation,
    SmWorkflowComponent.SplitPhaseServiceService.SubmitPartitionSemanticResultOperation,
    SmWorkflowComponent.SplitPhaseServiceService.SubmitHandoffScopeResultOperation,
    SmWorkflowComponent.SplitPhaseServiceService.SubmitReasoningResultOperation,
    SmWorkflowComponent.SplitPhaseServiceService.SubmitSplitHumanDirectionOperation
  )

  "Native workflow descriptions" should {
    "native JSON operation specifications" which {
      _operations.foreach { case (service, declared) =>
        s"connect $service.${declared.name}'s generated descriptions without replacing the runtime transport" in {
          Given("the actual generated CML operation, without injected description text or an executed handler")
          val content = declared.specification.content
          val optional = Set("advanceGoalPhase", "advanceRepositorySync", "advanceSplitPhase").contains(declared.name)
          val request = if (optional) WorkflowJsonCommandRequest.optionalDefinition else WorkflowJsonCommandRequest.definition

          When("the native JSON operation specification is assembled with its selected input domain")
          val actual = NativeWorkflowDescriptionMetadata.operation(declared.specification, spec.ResponseDefinition.void, request)

          Then("only generated descriptions are borrowed and the canonical JSON request/response contract remains native")
          actual.name shouldBe declared.name
          actual.content.nameAttributes shouldBe BaseContent.simple(declared.name).nameAttributes
          actual.content.descriptiveAttributes shouldBe content.descriptiveAttributes
          actual.summary shouldBe declared.specification.summary
          actual.description shouldBe declared.specification.description
          actual.request.parameters.head.domain.multiplicity shouldBe (if (optional) Multiplicity.ZeroOne else Multiplicity.One)
          actual.request should not be declared.specification.request
          actual.response shouldBe spec.ResponseDefinition.void
          actual.response should not be declared.specification.response
        }
      }
    }

    "existing typed response contracts" which {
      _typed_response_operations.foreach { declared =>
        s"retain ${declared.name}'s existing response while connecting its generated description" in {
          Given("the actual current generated result or Human completion specification and its existing typed response")
          val generated = declared.specification
          val response = generated.response

          When("the native JSON specification borrows only descriptions with that selected response")
          val actual = NativeWorkflowDescriptionMetadata.operation(generated, response)

          Then("the response object and JSON ingress are unchanged and no generated aliases or request fields are installed")
          actual.name shouldBe generated.name
          actual.content.nameAttributes shouldBe BaseContent.simple(generated.name).nameAttributes
          actual.content.descriptiveAttributes shouldBe generated.content.descriptiveAttributes
          actual.request.parameters.head.domain.multiplicity shouldBe Multiplicity.One
          actual.response should be theSameInstanceAs response
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
