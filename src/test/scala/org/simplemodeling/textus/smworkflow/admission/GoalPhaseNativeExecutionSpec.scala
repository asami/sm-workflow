package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration => HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DeliveryConfiguration, InstanceIdentity, LeasePolicy, MigrationPolicy, NamedWorkflowStore, RetentionPolicy, WorkflowDefinitionIdentity, WorkflowDefinitionRevision, WorkflowStoreIdentity}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** All descriptors and observations here are specification-owned data, never
  * genuine installed executor, reviewer, permission or provider provenance.
  * @since   Oct. 14, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseNativeExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val profile = WorkflowConcreteExecutionProfile("spec-current", "spec-provider", "spec-model", Map("effort" -> "bounded"))
  private val _other = profile.copy(identity = "spec-other", model = "spec-other-model")
  private val _origin = GoalPhaseNativeExecution.Origin(WorkflowExecutorDescriptor("spec-origin", profile), WorkflowExecutionPreference())
  private val _mapping = take(WorkflowExecutionProfileMapping.createC("spec-mapping", "1", Vector(profile, _other), Vector(
    WorkflowExecutionProfileMapping.ClassBinding("Routine", profile.identity),
    WorkflowExecutionProfileMapping.ClassBinding("Deep", _other.identity),
    WorkflowExecutionProfileMapping.ClassBinding("Review", profile.identity))))
  private def _profile_json(p: WorkflowConcreteExecutionProfile): Json = Json.obj(
    "identity" -> Json.fromString(p.identity), "provider" -> Json.fromString(p.provider), "model" -> Json.fromString(p.model),
    "controls" -> Json.obj(p.controls.toVector.map((k, v) => k -> Json.fromString(v))*))
  private val _origin_json = Json.obj("schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
    "current" -> Json.obj("contextIdentity" -> Json.fromString(_origin.current.contextIdentity), "profile" -> _profile_json(profile)),
    "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.False))
  private val _mapping_json = Json.obj("schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
    "identity" -> Json.fromString("spec-mapping"), "revision" -> Json.fromString("1"),
    "profiles" -> Json.arr(_profile_json(profile)), "classes" -> Json.arr(Json.obj(
      "reasoningClass" -> Json.fromString("Routine"), "profile" -> Json.fromString(profile.identity))))
  private def configuration(value: Json): ConfigurationValue = value.fold(ConfigurationValue.NullValue,
    ConfigurationValue.BooleanValue.apply, n => ConfigurationValue.NumberValue(n.toBigDecimal.get),
    ConfigurationValue.StringValue.apply, values => ConfigurationValue.ListValue(values.map(configuration).toList),
    obj => ConfigurationValue.ObjectValue(obj.toMap.view.mapValues(configuration).toMap))
  private def create(values: Map[String, ConfigurationValue]): ComponentCreate = {
    val id = SmWorkflowComponent.componentId
    ComponentCreate(RuntimeBindingAdmissionFixture.admit(Subsystem(
      name = "spec-execution", version = None,
      configuration = ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command)), ComponentOrigin.Builtin,
      Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name), version = Some("0.1.0"),
        schemaVersion = Some(3), componentId = Some(id))))
  }
  private def _resolve(values: Map[String, ConfigurationValue]): Consequence[Option[GoalPhaseNativeExecution.Origin]] = {
    var selected = Option.empty[GoalPhaseNativeExecution.Origin]
    val factory = new SmWorkflowConfiguredComponentFactory(Vector(GoalPhaseNativeExecution.declaration), Vector.empty,
      (_, parameters, _) => GoalPhaseNativeExecution.resolveOriginC(parameters).map { value =>
        selected = value
        Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
      })
    factory.primaryFactory.createPrimaryC(create(values)).map(_ => selected)
  }
  private val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("SpecGoal"),
    WorkflowDefinitionRevision("1"), InstanceIdentity("spec-instance"))
  private val request = ContinuationRequest(StateMachineRunIdentity("spec-run"), ContinuationIdentity("spec-work"),
    StateMachineRevision("2"), StateMachineRequiredOperationIdentity("semantic-work"),
    StateMachineOperationIdentity("SpecService", "work"), Some(TypedValue("SpecInput", "selected-task")),
    Some(StateMachineResultTypeReference("SpecResult")), ContextBundle("Assigned task", Vector.empty, Vector.empty, ContextSnapshot("1")),
    CompletionContract("spec-completion", Vector.empty), EvidenceContract("spec-evidence", Vector.empty),
    StateMachineOperationIdentity("SpecService", "submit"))
  private def work(name: String = "Routine", review: Boolean = false): WorkflowInteraction[String, Nothing] =
    WorkflowInteraction(handle, WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector(CapabilityRequirement("semantic-work")), RiskLevel("SOURCE"), ReasoningLevel.Standard,
        review, reasoningClass = Some(ReasoningClassReference(name))), MinimalPresentation("Assigned", "Waiting")))
  private def _order(value: WorkflowInteraction[String, Nothing]) = value.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
  private def metadata(example: String, rules: String) = afterWord(s"in spec:goal-phase-native-execution, example:$example, rules:$rules, phase:6")

  "Canonical Goal execution origin" should metadata("E1", "R1,R2") {
    "resolve structured and JSON values while leaving unused diagnostics inert" in {
      Given("canonical Component input with explicit specification-owned origin and unused command diagnostics")
      val decorated = _origin_json.deepMerge(Json.obj("diagnostics" -> Json.obj("grant" -> Json.True), "command" -> Json.True))
      When("normal Component parameter resolution consumes each declared representation")
      val selected = Vector(configuration(decorated), ConfigurationValue.StringValue(decorated.noSpaces))
        .map(v => _resolve(Map(GoalPhaseNativeExecution.declaration.name -> v)))
      Then("both forms retain precisely the explicit descriptor and preference without inferring execution")
      selected.foreach(value => take(value) shouldBe Some(_origin))
    }
    "retain absence and reject each malformed consumed origin member" in {
      Given("an absent key and origins missing schema, current, profile, controls or Boolean preferences")
      val bad = Vector(Json.Null, _origin_json.mapObject(_.remove("schemaVersion")),
        _origin_json.mapObject(_.add("schemaVersion", Json.fromString("wrong"))),
        _origin_json.mapObject(_.add("current", Json.Null)),
        _origin_json.mapObject(_.add("current", Json.obj("contextIdentity" -> Json.fromString(" padded "), "profile" -> _profile_json(profile)))),
        _origin_json.mapObject(_.add("current", Json.obj("contextIdentity" -> Json.fromString("spec"), "profile" -> Json.Null))),
        _origin_json.mapObject(_.add("current", Json.obj("contextIdentity" -> Json.fromString("spec"),
          "profile" -> _profile_json(profile).mapObject(_.remove("controls"))))),
        _origin_json.mapObject(_.add("current", Json.obj("contextIdentity" -> Json.fromString("spec"),
          "profile" -> _profile_json(profile.copy(model = " padded "))))),
        _origin_json.mapObject(_.add("preference", Json.Null)),
        _origin_json.mapObject(_.add("preference", Json.obj("forceInline" -> Json.fromString("true"), "isolateContext" -> Json.False))),
        _origin_json.mapObject(_.add("preference", Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.Null)))) ++
        Vector(profile.copy(identity = " "), profile.copy(provider = " padded "), profile.copy(controls = Map("effort" -> " ")))
          .map(p => _origin_json.mapObject(_.add("current", Json.obj("contextIdentity" -> Json.fromString("spec"), "profile" -> _profile_json(p)))))
      When("canonical resolution decodes the absent key and all consumed invalid values")
      val absent = _resolve(Map.empty)
      val rejected = bad.map(v => _resolve(Map(GoalPhaseNativeExecution.declaration.name -> configuration(v)))) ++
        Vector(_resolve(Map(GoalPhaseNativeExecution.declaration.name -> ConfigurationValue.StringValue("not-json"))),
          GoalPhaseNativeExecution.decodeC(null), GoalPhaseNativeExecution.resolveOriginC(null))
      Then("absence stays unconfigured and invalid input produces Consequence failures rather than inferred runtime data")
      take(absent) shouldBe None
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }
  "Fresh Goal selection" should metadata("E2", "R1,R3") {
    "select matching inline, different or isolated delegated work and required independent review" in {
      Given("actual canonical WorkOrders, a specification mapping and an explicit current descriptor")
      val cases = Vector(("Routine", false, WorkflowExecutionPreference(), ExecutionPlacement.Inline, ExecutionIndependence.Optional),
        ("Deep", false, WorkflowExecutionPreference(), ExecutionPlacement.Delegated, ExecutionIndependence.Optional),
        ("Routine", false, WorkflowExecutionPreference(isolateContext = true), ExecutionPlacement.Delegated, ExecutionIndependence.Optional),
        ("Review", true, WorkflowExecutionPreference(), ExecutionPlacement.Delegated, ExecutionIndependence.Required))
      When("the adapter binds each fresh requirement through the delivered resolver")
      val selected = cases.map { (name, review, preference, placement, independence) =>
        (take(GoalPhaseNativeExecution.bindIssuedC(work(name, review), Some(_mapping), Some(_origin.copy(preference = preference)))), placement, independence)
      }
      Then("the selected placement preserves origin and review independence")
      selected.foreach { (issued, placement, independence) =>
        _order(issued).requirement.disposition shouldBe Some(ExecutionDisposition(placement, independence, _origin.current.contextIdentity))
      }
    }
    "reject forced review or isolation and unmapped semantic work before issue" in {
      Given("review and isolated requirements that contradict forceInline plus missing or invalid semantic classes")
      val forced = _origin.copy(preference = WorkflowExecutionPreference(forceInline = true))
      val isolated = _origin.copy(preference = WorkflowExecutionPreference(forceInline = true, isolateContext = true))
      val invalid = work().copy(current = _order(work()).copy(requirement = _order(work()).requirement.copy(reasoningClass = Some(ReasoningClassReference(" ")))))
      val unclassified = work().copy(current = _order(work()).copy(requirement = _order(work()).requirement.copy(reasoningClass = None)))
      When("fresh binding attempts to resolve these contracts")
      val results = Vector(GoalPhaseNativeExecution.bindIssuedC(work("Review", true), Some(_mapping), Some(forced)),
        GoalPhaseNativeExecution.bindIssuedC(work(), Some(_mapping), Some(isolated)),
        GoalPhaseNativeExecution.bindIssuedC(work("Missing"), Some(_mapping), Some(_origin)),
        GoalPhaseNativeExecution.bindIssuedC(invalid, Some(_mapping), Some(_origin)),
        GoalPhaseNativeExecution.bindIssuedC(unclassified, Some(_mapping), Some(_origin)))
      Then("every contradiction fails without a selected interaction")
      results.foreach(_.isSuccess shouldBe false)
    }
    "admit only matching profile and placement observations through the generic resolver" in {
      Given("specification-owned resolved inline and independent review requirements")
      val inline = take(_mapping.resolveC(_order(work()).requirement, _origin.current))
      val review = take(_mapping.resolveC(_order(work("Review", true)).requirement, _origin.current))
      When("explicit descriptors contradict selected profile or context relationship")
      val rejected = Vector(inline.observeC(_origin.current.copy(contextIdentity = "spec-other-context")),
        review.observeC(_origin.current), review.observeC(WorkflowExecutorDescriptor("spec-reviewer", _other)))
      val valid = review.observeC(_origin.current.copy(contextIdentity = "spec-reviewer"))
      Then("only matching distinct-context fixture observation satisfies required review")
      rejected.foreach(_.isSuccess shouldBe false)
      take(valid).executorContextIdentity shouldBe "spec-reviewer"
      take(inline.observeC(_origin.current)).placement shouldBe ExecutionPlacement.Inline
    }
  }
  "Native interaction preservation" should metadata("E3", "R3,R4") {
    "change only the fresh requirement disposition and preserve legacy work" in {
      Given("one actual WorkOrder with its identity, request, context, contracts and presentation")
      val original = work()
      When("fresh mapped and unconfigured binding process the same WorkOrder")
      val selected = take(GoalPhaseNativeExecution.bindIssuedC(original, Some(_mapping), Some(_origin)))
      val legacy = take(GoalPhaseNativeExecution.bindIssuedC(original, None, None))
      Then("only selected disposition changes and omitted configuration returns the original interaction")
      selected.handle shouldBe original.handle
      _order(selected).request shouldBe _order(original).request
      _order(selected).presentation shouldBe _order(original).presentation
      _order(selected).requirement.copy(disposition = None) shouldBe _order(original).requirement
      legacy shouldBe original
    }
    "refuse already selected work and non-WorkOrder continuation" in {
      Given("a selected canonical WorkOrder and a Wait that has no executable work contract")
      val selected = take(GoalPhaseNativeExecution.bindIssuedC(work(), Some(_mapping), Some(_origin)))
      val waiting = WorkflowInteraction[String, Nothing](handle, WorkflowContinuation.Wait(StateMachineRevision("2"),
        request.context.snapshot, MinimalPresentation("Wait", "Waiting")))
      When("fresh binding is requested for those interactions")
      val repeated = GoalPhaseNativeExecution.bindIssuedC(selected, Some(_mapping), Some(_origin))
      val absent = GoalPhaseNativeExecution.bindIssuedC(waiting, None, None)
      Then("neither re-resolution nor treating a Wait as work succeeds")
      repeated.isSuccess shouldBe false
      absent.isSuccess shouldBe false
    }
  }
  "Goal host compatibility" should metadata("E4", "R2,R5,R6") {
    "validate omitted defaults and configured pairs while rejecting partial and null declarations" in {
      Given("an old source constructor with no new trailing arguments and explicit specification operational input")
      def _collection_(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "execution", name))
      val store = WorkflowStoreIdentity("spec-store")
      val d = GoalPhaseNativeRun.Declaration(Configuration(NamedWorkflowStore("workflow", store), MigrationPolicy("v1"),
        RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(store)), _collection_("instances"),
        _collection_("continuations"), GoalPhaseStartInput("PHASE-6", "repository"),
        RepositoryGitRuntimePrograms.Target(Path.of("/spec/repository"), "main", "origin", "main", "/spec/upstream.git"))
      When("declaration validation consumes legacy defaults, valid pairs and partial or null values")
      val configured = d.copy(executionProfiles = Some(_mapping), executionOrigin = Some(_origin))
      val bad = Vector(d.copy(executionProfiles = Some(_mapping)), d.copy(executionOrigin = Some(_origin)),
        d.copy(executionOrigin = null), d.copy(executionProfiles = Some(null)),
        configured.copy(executionOrigin = Some(null)), configured.copy(executionOrigin = Some(_origin.copy(current = null))),
        configured.copy(executionOrigin = Some(_origin.copy(preference = null))),
        configured.copy(executionOrigin = Some(_origin.copy(current = _origin.current.copy(profile = null)))))
      Then("legacy and complete operational input validate while every missing consumed value fails")
      GoalPhaseNativeRun.validateC(d).isSuccess shouldBe true
      GoalPhaseNativeRun.validateC(configured).isSuccess shouldBe true
      bad.foreach(value => GoalPhaseNativeRun.validateC(value).isSuccess shouldBe false)
      configured.executionOrigin.get.current shouldBe _origin.current
      configured.executionOrigin.get.current.profile shouldBe profile
    }
    "reject partial configuration through the canonical normal host factory" in {
      Given("normal host values and persistence settings with exactly one operational half configured")
      val prefix = "sm-workflow.goal-phase.persistence."
      val base = ThreeWorkflowNormalHostFixture.values(Path.of("/spec/repository")) ++ Map(
        prefix + "store-name" -> "workflow", prefix + "store-identity" -> "spec-store", prefix + "migration-policy" -> "v1",
        prefix + "retention-policy" -> "v1", prefix + "lease-policy" -> "v1",
        prefix + "instance-collection" -> ThreeWorkflowNormalHostFixture.collection("instances"),
        prefix + "continuation-collection" -> ThreeWorkflowNormalHostFixture.collection("continuations"))
      val partial = Vector(Map(WorkflowExecutionProfileParameters.declaration.name -> _mapping_json.noSpaces),
        Map(GoalPhaseNativeExecution.declaration.name -> _origin_json.noSpaces))
      When("the actual normal factory consumes each canonical parameter snapshot")
      val rejected = partial.map(extra => ThreeWorkflowRuntimeParameters.factory.primaryFactory.createPrimaryC(
        create((base ++ extra).view.mapValues(ConfigurationValue.StringValue.apply).toMap)))
      Then("both reject the missing operational half before storage or execution can be bound")
      rejected.foreach { result =>
        result.isSuccess shouldBe false
        result.toString should include("requires both profiles and origin")
      }
      GoalPhaseNativeExecution.validatePairC(null, None).isSuccess shouldBe false
      GoalPhaseNativeExecution.validatePairC(None, None).isSuccess shouldBe true
    }
    "require execution observation even when configured names and selected disposition are valid" in {
      Given("a mapped canonical issue and a semantic reply carrying only specification worker and model names")
      val issued = take(GoalPhaseNativeExecution.bindIssuedC(work(), Some(_mapping), Some(_origin)))
      val reply = ContinuationResult(issued.handle, request.runId, request.continuationId, request.expectedRevision,
        request.context.snapshot, TypedValue("SpecResult", "completed"), ContextReference("spec-result", "1"), Vector.empty,
        ExecutionEvidence(Vector.empty, Some("spec-worker"), Some(profile.model)))
      When("existing native result admission receives names alone, then a specification-owned matching observation")
      val missing = admitResultC(issued, reply)
      val observed = reply.copy(evidence = reply.evidence.copy(execution = Some(ExecutionObservation(ExecutionPlacement.Inline,
        _origin.current.contextIdentity, _origin.current.contextIdentity, profile.identity))))
      Then("names grant no admission while explicit observation satisfies only the structural execution contract")
      missing.isSuccess shouldBe false
      admitResultC(issued, observed).isSuccess shouldBe true
    }
  }
}
