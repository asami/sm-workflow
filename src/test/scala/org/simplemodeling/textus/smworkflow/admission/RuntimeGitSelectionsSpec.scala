package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.processexecution.{GitOperation, GitSelectionId, GitSelectionParameters}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Check actual data delivered through deferred handlers, without executing
  * a command or pretending this data supplies host effect authority.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RuntimeGitSelectionsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Runtime Git data failed: $other")
  }
  private def probe(observe: ActionCall.Core => Unit): spec.ServiceDefinition = {
    val declared = SmWorkflowComponent.GoalPhaseServiceService
    val operation = new spec.OperationDefinition {
      val specification = NativeWorkflowDescriptionMetadata.operation(declared.AdvanceGoalPhaseOperation)
      def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = incoming
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          def execute(): Consequence[OperationResponse] = {
            observe(core)
            Consequence.success(OperationResponse.RecordResponse(Record.empty))
          }
        }
      })
    }
    NativeWorkflowDescriptionMetadata.service(declared, spec.OperationDefinitionGroup(NonEmptyVector.one(operation)))
  }
  private def invoke(service: spec.ServiceDefinition, context: ExecutionContext): Unit = {
    val request = Request.of(component = "SmWorkflow", service = service.name, operation = "advanceGoalPhase")
    val command = take(service.operations.operations.head.createOperationRequest(request)).asInstanceOf[CommandAction]
    val call = command.createCall(ActionCall.Core(command, context, None, None))
    take(call.execute().flatMap(call.afterCommitResponseC))
  }
  "Actual run Git selection data" should {
    "retain recorded staging ordinals and include only admitted inventory in the Phase release" in {
      Given("a current Plan with one closed Step, an admitted literal repair file and an unchanged registered prefix")
      val plan = GoalPhaseOrderedProgression.Plan("inventory", "2", "PHASE-fixture", "project", Vector(
        GoalPhaseOrderedProgression.Step("S1", "Original Step", Vector("src/First.scala"), Vector.empty, Vector("test"))),
        Vector("test"), phaseRepairInventory = Vector("src/Repair.scala"))
      val registered = Vector("src/First.scala", "docs/Reserved.md", "src/Repair.scala")
      var observed = Vector.empty[GitSelectionParameters]
      val target = probe { core =>
        Vector("goal-path-1", "goal-path-3", "goal-step-1", "goal-step-2").foreach { name =>
          val id = take(GitSelectionId.parseC(name))
          observed :+= RuntimeGitSelections.selected(core, GitOperation.StageSelected(id)).get
        }
      }
      When("standard composition supplies the retained finite registration to a deferred handler")
      invoke(take(RuntimeGitSelections.goalC(target, plan, registered)), ExecutionContext.create())
      Then("the suffix never renumbers old paths and the release consumes the admitted inventory without unrelated registrations")
      observed.map(_.paths.map(_.value)) shouldBe Vector(Vector("src/First.scala"), Vector("src/Repair.scala"),
        Vector("src/First.scala"), Vector("src/First.scala", "src/Repair.scala"))
    }

    "reach deferred command handlers using each actual Plan's paths and Step purpose" in {
      Given("two real Plan values and the same generated Operation contract")
      val context = ExecutionContext.create()
      val first = GoalPhaseOrderedProgression.Plan("first", "1", "PHASE-7", "project", Vector(
        GoalPhaseOrderedProgression.Step("S1", "Implement first behavior", Vector("src/First.scala"), Vector.empty, Vector("test"))), Vector("test"))
      val second = first.copy(identity = "second", phase = "PHASE-8", steps = Vector(first.steps.head.copy(
        purpose = "Implement next behavior", ownedPaths = Vector("src/Next.scala"))))
      val id = take(GitSelectionId.parseC("goal-step-1"))
      var observed = Vector.empty[GitSelectionParameters]
      val target = probe { core =>
        core.executionContext should be theSameInstanceAs context
        observed :+= RuntimeGitSelections.selected(core, GitOperation.CommitSelected(id)).get
      }
      When("the deferred runtime composes and invokes each concrete handler")
      Vector(first, second).foreach { plan =>
        val service = DeferredWorkflowServices.bind(Vector(target), (_, _) => RuntimeGitSelections.goalC(target, plan).map(Vector(_))).head
        invoke(service, context)
      }
      Then("typed path/message data follows the actual Plan without changing the actor/UoW context or sharing a prior run")
      observed.map(_.paths.map(_.value)) shouldBe Vector(Vector("src/First.scala"), Vector("src/Next.scala"))
      observed.flatMap(_.message) shouldBe Vector("PHASE-7: Implement first behavior", "PHASE-8: Implement next behavior")
    }

    "leave existing fixed-program handlers without new per-run selection data" in {
      Given("an existing handler without standard-run composition")
      val id = take(GitSelectionId.parseC("goal-step-1"))
      var observed = Option.empty[Option[GitSelectionParameters]]
      val service = probe(core => observed = Some(RuntimeGitSelections.selected(core, GitOperation.CommitSelected(id))))
      When("the fixed handler is invoked normally")
      invoke(service, ExecutionContext.create())
      Then("the ordinary fixed CNCF program lookup remains selected")
      observed shouldBe Some(None)
    }
  }
}
