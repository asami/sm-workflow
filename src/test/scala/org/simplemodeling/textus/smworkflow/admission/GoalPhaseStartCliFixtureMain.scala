package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.CncfMain
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, StateMachineOperationIdentity, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.impl.{SmWorkflowParticipantFactoryBase, SmWorkflowPrimaryComponent}
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput}

/**
 * Test-classpath-only factory used by the forked CncfMain smoke command.
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseStartCliFixtureFactory extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory = GoalPhaseStartCliFixturePrimaryFactory
}

private object GoalPhaseStartCliFixturePrimaryFactory
    extends SmWorkflowParticipantFactoryBase with Component.PrimaryComponentFactory {
  import ProfileStartAdmission.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => throw new IllegalStateException(s"GoalPhase CLI fixture failed: $failure")
  }

  private def resource(path: String): String = {
    val stream = getClass.getResourceAsStream(path)
    if (stream == null) throw new IllegalStateException(s"Missing GoalPhase CLI fixture: $path")
    val source = scala.io.Source.fromInputStream(stream, "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }

  private val sidecar = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val workflow = take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), sidecar,
    "src/main/cozy/sm-workflow.cml"))
  private val definition = take(WorkflowInstancePersistence.bindCandidateDefinitionC(workflow))
  private val configuration = Configuration(
    NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
  )
  private val selection = Selection(
    "cli-selection-1", StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"),
    WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("goal-phase-ordered-run-v4"),
    "cli-participant-1", SelectionOrigin.ExplicitHuman, "cli-start-1", None,
    Some(GoalPhaseStartCommandAdapter.selectedInput(GoalPhaseStartInput("77.1", "cncf")))
  )
  private val selections = new SelectionPort {
    def loadC(selectionId: String): Consequence[Option[Selection]] =
      Consequence.success(Option(selection).filter(_.selectionId == selectionId))
  }
  private var stored: Option[InstanceRecord] = None
  private val instances = new WorkflowInstancePersistence {
    def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
      if (config != configuration || stored.nonEmpty)
        Consequence.stateConflict("GoalPhase CLI fixture create conflict")
      else record.validateC.map { valid => stored = Some(valid); valid }

    def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
      if (config != configuration) Consequence.stateConflict("GoalPhase CLI fixture store mismatch")
      else Consequence.success(stored.filter(_.identity == identity))

    def append(config: Configuration, identity: InstanceIdentity,
      expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
      if (config != configuration) Consequence.stateConflict("GoalPhase CLI fixture store mismatch")
      else stored match {
        case Some(current) if current.identity == identity =>
          current.appendC(expectedRevision, entry).map { next => stored = Some(next); next }
        case _ => Consequence.stateConflict("GoalPhase CLI fixture instance is missing")
      }
  }

  override protected def create_Component(params: ComponentCreate): Component =
    new SmWorkflowPrimaryComponent()

  override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
    val operation = take(GoalPhaseStartServiceOperation.bindC(
      selections, definition, configuration, instances))
    val service = spec.ServiceDefinition("GoalPhaseService",
      spec.OperationDefinitionGroup(cats.data.NonEmptyVector.of(operation)))
    spec_create(SmWorkflowComponent.name, SmWorkflowComponent.componentId, service)
  }
}

/** Run with `Test / run / fork := true` so CncfMain and the fixture factory
  * execute in a distinct JVM, without registering a production provider.
  */
object GoalPhaseStartCliFixtureMain {
  def main(args: Array[String]): Unit = {
    val command = GoalPhaseStartCommand(
      GoalPhaseStartInput("77.1", "cncf"), "cli-selection-1", "cli-participant-1", "cli-start-1")
    CncfMain.main(Array(
      "--component-factory-class", classOf[GoalPhaseStartCliFixtureFactory].getName,
      "--textus.test.descriptor=src/test/resources/workflow/goal-phase-cli-test-descriptor.yaml",
      "command",
      s"${SmWorkflowComponent.name}/GoalPhaseService/startGoalPhase",
      "--json", GoalPhaseStartServiceOperation.encodeCommand(command)
    ))
  }
}
