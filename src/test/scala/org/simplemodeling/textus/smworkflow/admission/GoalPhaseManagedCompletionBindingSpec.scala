package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import java.time.Instant
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Request
import org.goldenport.protocol.spec as spec
import org.goldenport.value.BaseContent
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * External application-package binding admission, not a completion/reopen spec.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseManagedCompletionBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Managed binding fixture rejected: $failure")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }
  private val artifact = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val workflow = take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), artifact, "src/main/cozy/sm-workflow.cml"))
  private val definition = take(bindCandidateDefinitionC(workflow))
  private val configuration = Configuration(
    NamedWorkflowStore("workflow", WorkflowStoreIdentity("managed-binding-store")),
    MigrationPolicy("binding-v1"), RetentionPolicy("binding-v1"), LeasePolicy("binding-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("managed-binding-store")))
  private val component = SmWorkflowComponent.componentId
  private val completion = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "goalbinding", name))
  private val instances = collection("instances")
  private val continuations = collection("continuations")
  private val policy = take(EventPersistencePolicy.createC("goal-binding-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("binding-transient"), EventPersistencePolicy.Mode.Transient))))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("events"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(component, "workflow", instances, continuations))))
  private val issued = new IssuedWorkOrderPersistence.InMemory[StepCloseRequest]
  private var unexpectedCalls = 0
  private val unavailableInstances = new WorkflowInstancePersistence {
    def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] = {
      unexpectedCalls += 1; Consequence.stateConflict("binding must not create an instance")
    }
    def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] = {
      unexpectedCalls += 1; Consequence.stateConflict("binding must not load an instance")
    }
    def append(config: Configuration, identity: InstanceIdentity, revision: InstanceRevision,
        entry: HistoryEntry): Consequence[InstanceRecord] = {
      unexpectedCalls += 1; Consequence.stateConflict("binding must not append an instance")
    }
  }
  private val legacyUow: () => UnitOfWork = () => {
    unexpectedCalls += 1
    throw new IllegalStateException("binding must not run a legacy UoW")
  }
  private def withManaged(codec: WorkflowResultJsonV1.PayloadCodec[ReviewStepResult])(
      body: WorkflowManagedCompletion[ReviewStepResult] => Unit): Unit = {
    val context = ExecutionContext.create()
    val scope = ScopeContext(ScopeKind.Subsystem, "goal-binding", None, context.observability)
    val path = Files.createTempDirectory("goal-managed-binding-").resolve("host.sqlite")
    val configured = ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
    val host = take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, configured))
    try {
      val managed = take(WorkflowManagedCompletion.bindC(host.subsystem, component,
        configuration, completion, Vector(definition), instances, continuations,
        GoalPhaseStepCloseJson.payloadCodec, codec, StateMachineResultTypeReference("ReviewStepResult"),
        (_, _) => Consequence.success(Vector.empty)))
      body(managed)
    } finally take(host.shutdownC())
  }
  private def bind(managed: WorkflowManagedCompletion[ReviewStepResult]) =
    GoalPhaseReviewCompletionOperation.bindManagedC(artifact, () => Instant.EPOCH,
      issued, unavailableInstances, configuration, legacyUow, managed)

  "GoalPhase managed completion binding" should {
    "bind the generated typed Operation without a legacy runtime or storage effects" in {
      Given("the owning SQLite Subsystem and a managed completion with the canonical review result codec")
      withManaged(GoalPhaseReviewCompletionOperation.payloadCodec) { managed =>
        When("the generated review completion operation is bound to that managed dependency")
        val operation = take(bind(managed))
        Then("the canonical submitStepReview operation retains only its generated descriptions and native transport")
        operation.specification.name shouldBe "submitStepReview"
        operation.specification.content.descriptiveAttributes shouldBe
          SmWorkflowComponent.GoalPhaseServiceService.SubmitStepReviewOperation.specification.content.descriptiveAttributes
        operation.specification.content.nameAttributes shouldBe BaseContent.simple("submitStepReview").nameAttributes
        operation.specification.request should be theSameInstanceAs WorkflowJsonCommandRequest.definition
        operation.specification.response should be theSameInstanceAs spec.ResponseDefinition.void
        When("an incomplete request attempts to create the operation call")
        val result = operation.createOperationRequest(Request.of(component = component.name,
          service = "GoalPhaseService", operation = "submitStepReview"))
        Then("it is refused without legacy runtime or storage effects")
        result shouldBe a[Consequence.Failure[?]]
        unexpectedCalls shouldBe 0
      }
    }

    "reject an absent managed dependency without selecting a legacy fallback" in {
      Given("an absent managed completion dependency")
      val dependency: WorkflowManagedCompletion[ReviewStepResult] = null
      When("the review completion operation is bound without that dependency")
      val result = bind(dependency)
      Then("binding fails without selecting the legacy instance or UoW ports")
      result shouldBe a[Consequence.Failure[?]]
      unexpectedCalls shouldBe 0
    }

    "reject a managed result type inconsistent with the generated review Operation" in {
      Given("an otherwise managed completion whose payload codec declares a foreign result type")
      val wrong = new WorkflowResultJsonV1.PayloadCodec[ReviewStepResult] {
        val typeIdentity = "ForeignReviewResult"
        def encode(value: ReviewStepResult): Json = GoalPhaseReviewCompletionOperation.payloadCodec.encode(value)
        def decode(value: Json): Either[String, ReviewStepResult] =
          GoalPhaseReviewCompletionOperation.payloadCodec.decode(value)
      }
      withManaged(wrong) { managed =>
        When("the canonical review operation is bound to that foreign result declaration")
        val result = bind(managed)
        Then("the type mismatch is refused without legacy effects")
        result shouldBe a[Consequence.Failure[?]]
        unexpectedCalls shouldBe 0
      }
    }

    "reject missing host follow-up ports before advertising the Operation" in {
      Given("a canonical managed completion with explicitly absent host follow-up ports")
      withManaged(GoalPhaseReviewCompletionOperation.payloadCodec) { managed =>
        When("the auto-close port is declared present but is null")
        val autoclose = GoalPhaseReviewCompletionOperation.bindManagedC(artifact, () => Instant.EPOCH,
          issued, unavailableInstances, configuration, legacyUow, managed,
          autoClose = Some(null))
        Then("the missing auto-close dependency prevents operation advertisement")
        autoclose shouldBe a[Consequence.Failure[?]]
        When("the repair-loop port is declared present but is null")
        val repairloop = GoalPhaseReviewCompletionOperation.bindManagedC(artifact, () => Instant.EPOCH,
          issued, unavailableInstances, configuration, legacyUow, managed,
          repairLoop = Some(null))
        Then("the missing repair dependency is refused without legacy effects")
        repairloop shouldBe a[Consequence.Failure[?]]
        unexpectedCalls shouldBe 0
      }
    }
  }
}
