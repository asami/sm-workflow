package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.host.NativeWorkflowHostExecution
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseWorkClassification.*
import RepositorySyncNativeExecution as P
import SplitPhaseExplicitPartitionFixture.take

/** Shared specification inputs only: no installed provider or authority.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object RepositoryExecutionFixture {
  val profile = WorkflowConcreteExecutionProfile("spec-repository-profile", "spec-provider", "spec-model", Map("effort" -> "bounded"))
  val origin = GoalPhaseNativeExecution.Origin(WorkflowExecutorDescriptor("spec-repository-origin", profile), WorkflowExecutionPreference())
  val classifications = P.Classifications(Classification(Kind.REVIEW, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL),
    Classification(Kind.CODING, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL))
  def classificationJson(value: P.Classifications): Json = Json.obj((Vector("schemaVersion" -> Json.fromString(P.schemaVersion),
    "review" -> value.review.asJson, "repair" -> value.repair.asJson) ++ value.conflict.toVector.map(value => "conflict" -> value.asJson))*)
  private def _profile_json(value: WorkflowConcreteExecutionProfile): Json = Json.obj(
    "identity" -> Json.fromString(value.identity), "provider" -> Json.fromString(value.provider), "model" -> Json.fromString(value.model),
    "controls" -> Json.obj(value.controls.toVector.map((key, text) => key -> Json.fromString(text))*))
  def parameters(replacement: Boolean = false, conflict: Option[Classification] = None): Map[String, String] = {
    val paired = classifications.copy(conflict = conflict)
    val selected = if (replacement) paired.copy(review = paired.review.copy(complexity = Complexity.COMPLEX)) else paired
    val current = if (replacement) profile.copy(identity = "spec-later-profile", model = "spec-later-model") else profile
    val mapping = Json.obj("schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
      "identity" -> Json.fromString(if (replacement) "spec-later-mapping" else "spec-repository-mapping"),
      "revision" -> Json.fromString(if (replacement) "2" else "1"), "profiles" -> Json.arr(_profile_json(current)),
      "classes" -> Json.fromValues(Vector(if (replacement) "ReviewCritical" else "ReviewStandard",
        if (replacement && conflict.nonEmpty) "CodingProgrammingDeep" else "CodingProgrammingStandard")
        .map(name => Json.obj("reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(current.identity)))))
    val from = Json.obj("schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
      "current" -> Json.obj("contextIdentity" -> Json.fromString(if (replacement) "spec-later-origin" else origin.current.contextIdentity),
        "profile" -> _profile_json(current)), "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.False))
    Map(P.declaration.name -> classificationJson(selected).noSpaces, WorkflowExecutionProfileParameters.declaration.name -> mapping.noSpaces,
      GoalPhaseNativeExecution.declaration.name -> from.noSpaces)
  }
  val mapping = take(WorkflowExecutionProfileParameters.decodeC(take(io.circe.parser.parse(
    parameters()(WorkflowExecutionProfileParameters.declaration.name)).fold(e => Consequence.stateConflict(e.message), Consequence.success))))
  def evidence(prepared: NativeWorkflowHostExecution.Prepared, reference: ContextReference, worker: String): ExecutionEvidence = {
    val selection = prepared.session.nextSelection.get
    val context = if (prepared.session.requirement.disposition.get.placement == ExecutionPlacement.Inline)
      prepared.session.origin.contextIdentity else worker
    val completed = take(prepared.recordC(selection, WorkflowExecutionHarness.Outcome(WorkflowExecutionHarness.Status.Completed,
      WorkflowExecutionHarness.Reason.Completed, "Specification-owned executor returned assigned work",
      Some(WorkflowExecutorDescriptor(context, selection.profile)))))
    val public = take(completed.completionEvidenceC(Vector(V.WorkflowArtifactReference(reference.identity, reference.revision))))
    val observation = public.execution.get
    ExecutionEvidence(Vector(reference), public.workerIdentity, public.modelIdentity, execution = Some(ExecutionObservation(
      ExecutionPlacement.values.find(_.value == observation.placement).get, observation.originContextIdentity,
      observation.executorContextIdentity, observation.profileIdentity)))
  }
}

/** Finite adapter examples; all executor facts are specification-owned.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class RepositorySyncNativeExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositoryExecutionFixture.*
  private val _handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("SpecRepository"),
    WorkflowDefinitionRevision("1"), InstanceIdentity("spec-repository"))
  private val _request = ContinuationRequest(StateMachineRunIdentity("spec-run"), ContinuationIdentity("spec-work"),
    StateMachineRevision("2"), StateMachineRequiredOperationIdentity("semantic-work"), StateMachineOperationIdentity("Spec", "work"),
    Some(TypedValue("SpecInput", "assigned")), Some(StateMachineResultTypeReference("SpecResult")),
    ContextBundle("Assigned", Vector.empty, Vector.empty, ContextSnapshot("1")), CompletionContract("spec-completion", Vector.empty),
    EvidenceContract("spec-evidence", Vector.empty), StateMachineOperationIdentity("Spec", "submit"))
  private def _work(review: Boolean): WorkflowInteraction[String, Nothing] = WorkflowInteraction(_handle,
    WorkflowContinuation.WorkOrder(_request, ExecutionRequirement(Vector(CapabilityRequirement("semantic-work")),
      RiskLevel("SOURCE_MERGE"), ReasoningLevel.Standard, review), MinimalPresentation("Assigned", "Pending")))
  private def _order(value: WorkflowInteraction[String, Nothing]) = value.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
  private def _configuration(value: Json): ConfigurationValue = value.fold(ConfigurationValue.NullValue,
    ConfigurationValue.BooleanValue.apply, n => ConfigurationValue.NumberValue(n.toBigDecimal.get), ConfigurationValue.StringValue.apply,
    xs => ConfigurationValue.ListValue(xs.map(_configuration).toList),
    obj => ConfigurationValue.ObjectValue(obj.toMap.view.mapValues(_configuration).toMap))
  private def _resolve(values: Map[String, ConfigurationValue]): Consequence[Option[P.Classifications]] = {
    val id = SmWorkflowComponent.componentId
    val creation = ComponentCreate(RuntimeBindingAdmissionFixture.admit(Subsystem(name = "spec-repository-execution", version = None,
      configuration = ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty), aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command)), ComponentOrigin.Builtin, Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))))
    var selected = Option.empty[P.Classifications]
    new SmWorkflowConfiguredComponentFactory(Vector(P.declaration), Vector.empty, (_, parameters, _) => P.resolveC(parameters).map { value =>
      selected = value
      Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
    }).primaryFactory.createPrimaryC(creation).map(_ => selected)
  }

  "Repository semantic execution input" should afterWord("in spec:repository-sync-native-execution, example:E1, rules:R1,R2") {
    "resolve explicit structured and string pairs and refuse malformed consumed input" in {
      Given("explicit review and repair classifications with inert diagnostics, and missing or malformed consumed fields")
      val value = classificationJson(classifications).deepMerge(Json.obj("diagnostics" -> Json.obj("grant" -> Json.True), "provider" -> Json.True))
      val bad = Vector(Json.Null, value.mapObject(_.remove("schemaVersion")), value.mapObject(_.add("schemaVersion", Json.Null)),
        value.mapObject(_.add("schemaVersion", Json.fromString("wrong"))), value.mapObject(_.remove("review")),
        value.mapObject(_.remove("repair")), value.mapObject(_.add("review", Json.Null)), value.mapObject(_.add("repair", Json.Null))) ++
        Vector("kind", "complexity", "domain", "footprint").flatMap { key =>
          Vector(value.mapObject(_.add("review", classifications.review.asJson.mapObject(_.remove(key)))),
            value.mapObject(_.add("review", classifications.review.asJson.mapObject(_.add(key, Json.fromString("UNKNOWN"))))),
            value.mapObject(_.add("repair", classifications.repair.asJson.mapObject(_.add(key, Json.Null)))))
        } ++ Vector(classificationJson(classifications.copy(review = classifications.repair)),
          classificationJson(classifications.copy(repair = classifications.review)))
      When("ordinary Component resolution consumes absence, both representations and invalid definitions")
      val valid = Vector(_configuration(value), ConfigurationValue.StringValue(value.noSpaces)).map(v => _resolve(Map(P.declaration.name -> v)))
      val rejected = bad.map(v => _resolve(Map(P.declaration.name -> _configuration(v)))) ++ Vector(
        _resolve(Map(P.declaration.name -> ConfigurationValue.StringValue("not-json"))), P.decodeC(null), P.resolveC(null),
        P.validateC(null, None, None), P.validateC(Some(null), None, None), P.validateC(Some(classifications.copy(review = null)), None, None),
        P.validateC(Some(classifications.copy(repair = classifications.repair.copy(domain = null))), None, None))
      Then("absence stays absent, diagnostics stay inert, and every consumed malformed value fails")
      take(_resolve(Map.empty)) shouldBe None
      valid.foreach(v => take(v) shouldBe Some(classifications))
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }
  "Explicit Repository classification" should afterWord("in spec:repository-sync-native-execution, example:E2, rules:R1,R2") {
    "derive semantic classes without changing assignment risk capabilities or presentation" in {
      Given("fresh review and repair WorkOrders with explicit STANDARD PROGRAMMING SMALL classifications")
      val cases = Vector((true, P.Work.Review, "ReviewStandard"), (false, P.Work.Repair, "CodingProgrammingStandard"))
      val alternate = classifications.copy(review = classifications.review.copy(footprint = Footprint.LARGE),
        repair = classifications.repair.copy(domain = Domain.ENGINEERING, complexity = Complexity.COMPLEX))
      When("the finite adapter applies explicit semantic inputs without concrete mapping")
      val selected = cases.map { (review, kind, name) =>
        (_work(review), take(P.bindIssuedC(_work(review), kind, Some(classifications), None, None)), name)
      }
      val deep = take(P.bindIssuedC(_work(false), P.Work.Repair, Some(alternate), None, None))
      val critical = take(P.bindIssuedC(_work(true), P.Work.Review, Some(alternate), None, None))
      Then("only semantic reasoning changes and alternate semantic rules remain those of the existing classifier")
      selected.foreach { (before, after, name) =>
        after.handle shouldBe before.handle
        _order(after).request shouldBe _order(before).request
        _order(after).presentation shouldBe _order(before).presentation
        _order(after).requirement.copy(reasoningClass = None) shouldBe _order(before).requirement
        _order(after).requirement.reasoningClass shouldBe Some(ReasoningClassReference(name))
        _order(after).requirement.disposition shouldBe None
      }
      _order(deep).requirement.reasoningClass shouldBe Some(ReasoningClassReference("CodingEngineeringDeep"))
      _order(critical).requirement.reasoningClass shouldBe Some(ReasoningClassReference("ReviewCritical"))
    }
  }
  "Canonical Repository selection" should afterWord("in spec:repository-sync-native-execution, example:E3, rules:R2,R3") {
    "select inline repair and delegated independent review and refuse incomplete or contradictory inputs" in {
      Given("one canonical mapping and origin plus explicit semantic classes and fresh actual WorkOrders")
      val missing = take(WorkflowExecutionProfileMapping.createC("spec-partial", "1", Vector(profile),
        Vector(WorkflowExecutionProfileMapping.ClassBinding("CodingProgrammingStandard", profile.identity))))
      When("fresh binding delegates concrete selection to CNCF and consumes invalid options and kinds")
      val review = take(P.bindIssuedC(_work(true), P.Work.Review, Some(classifications), Some(mapping), Some(origin)))
      val repair = take(P.bindIssuedC(_work(false), P.Work.Repair, Some(classifications), Some(mapping), Some(origin)))
      val failures = Vector(P.bindIssuedC(_work(true), P.Work.Review, Some(classifications), Some(missing), Some(origin)),
        P.bindIssuedC(_work(true), P.Work.Review, None, Some(mapping), Some(origin)),
        P.bindIssuedC(_work(false), P.Work.Review, Some(classifications), Some(mapping), Some(origin)),
        P.bindIssuedC(_work(true), P.Work.Repair, Some(classifications), Some(mapping), Some(origin)),
        P.bindIssuedC(_work(true), null, Some(classifications), Some(mapping), Some(origin)),
        P.bindIssuedC[String](null, P.Work.Review, Some(classifications), None, None),
        P.validateC(Some(classifications), Some(mapping), None), P.validateC(Some(classifications), None, Some(origin)),
        P.validateC(Some(classifications), null, None), P.validateC(Some(classifications), None, null),
        P.validateC(Some(classifications), Some(null), Some(origin)), P.validateC(Some(classifications), Some(mapping), Some(null)))
      val isolated = take(P.bindIssuedC(_work(false), P.Work.Repair, Some(classifications), Some(mapping),
        Some(origin.copy(preference = WorkflowExecutionPreference(isolateContext = true)))))
      val prepared = take(NativeWorkflowHostExecution.prepareC(take(NativeWorkflowIssuedWorkContext.fromIssuedC(review,
        NativeWorkflowIssuedWorkContext.Mode.Review, new WorkflowResultJsonV1.PayloadCodec[String] {
          val typeIdentity = "SpecInput"
          def encode(value: String) = Json.fromString(value)
          def decode(value: Json) = value.asString.toRight("Expected text")
        })), mapping, origin.current))
      Then("saved requirements retain explicit scope and preferences with no missing-class fallback")
      _order(review).requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Delegated, ExecutionIndependence.Required, origin.current.contextIdentity))
      _order(repair).requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Inline, ExecutionIndependence.Optional, origin.current.contextIdentity))
      _order(isolated).requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
      prepared.session.mappingIdentity shouldBe mapping.identity
      prepared.session.mappingRevision shouldBe mapping.revision
      prepared.session.origin shouldBe origin.current
      failures.foreach(_.isSuccess shouldBe false)
    }
  }
  "Repository legacy and retained work" should afterWord("in spec:repository-sync-native-execution, example:E4, rules:R2,R4,R5") {
    "preserve omitted configuration and logical-only input and refuse rebinding selected work" in {
      Given("an unmapped original interaction, explicit logical-only classes and a selected issue retained by its owner")
      val original = _work(false)
      val selected = take(P.bindIssuedC(original, P.Work.Repair, Some(classifications), Some(mapping), Some(origin)))
      val stored = selected
      val wait = WorkflowInteraction[String, Nothing](_handle, WorkflowContinuation.Wait(StateMachineRevision("2"),
        _request.context.snapshot, MinimalPresentation("Wait", "Pending")))
      When("fresh binding consumes omitted or logical-only configuration and is incorrectly attempted on selected work or Wait")
      val legacy = take(P.bindIssuedC(original, P.Work.Repair, None, None, None))
      val logical = take(P.bindIssuedC(original, P.Work.Repair, Some(classifications), None, None))
      val repeated = P.bindIssuedC(stored, P.Work.Repair, Some(classifications), Some(mapping), Some(origin))
      val cleared = P.bindIssuedC(stored, P.Work.Repair, None, None, None)
      val waiting = P.bindIssuedC(wait, P.Work.Repair, None, None, None)
      Then("legacy work is unchanged, logical classification has no disposition and retained selection cannot be replaced")
      legacy shouldBe original
      _order(logical).requirement.reasoningClass shouldBe Some(ReasoningClassReference("CodingProgrammingStandard"))
      _order(logical).requirement.disposition shouldBe None
      repeated.isSuccess shouldBe false
      cleared.isSuccess shouldBe false
      waiting.isSuccess shouldBe false
      stored shouldBe selected
    }
  }
  "Optional Repository conflict classification" should afterWord("in spec:repository-sync-native-execution, example:E7, rules:R1,R2,R3,R4,R5") {
    "consume only explicit optional CODING input and retain old paired definitions" in {
      Given("the original two-field constructor and JSON plus explicit conflict CODING with inert unused diagnostics")
      val paired = P.Classifications(classifications.review, classifications.repair)
      val explicit = paired.copy(conflict = Some(classifications.repair))
      val value = classificationJson(explicit).deepMerge(Json.obj("diagnostics" -> Json.obj("conflict" -> Json.True)))
      val malformed = Vector(value.mapObject(_.add("conflict", Json.Null)),
        value.mapObject(_.add("conflict", Json.True)),
        value.mapObject(_.add("conflict", classifications.review.asJson))) ++
        Vector("kind", "complexity", "domain", "footprint").flatMap { key =>
          Vector(value.mapObject(_.add("conflict", classifications.repair.asJson.mapObject(_.remove(key)))),
            value.mapObject(_.add("conflict", classifications.repair.asJson.mapObject(_.add(key, Json.Null)))),
            value.mapObject(_.add("conflict", classifications.repair.asJson.mapObject(_.add(key, Json.fromString("UNKNOWN"))))))
        } ++ Kind.values.filter(_ != Kind.CODING).map(kind =>
          value.mapObject(_.add("conflict", classifications.repair.copy(kind = kind).asJson)))
      When("ordinary Component resolution consumes both representations and validates directly supplied optional values")
      val valid = Vector(_configuration(value), ConfigurationValue.StringValue(value.noSpaces)).map(v => _resolve(Map(P.declaration.name -> v)))
      val old = Vector(_configuration(classificationJson(paired)), ConfigurationValue.StringValue(classificationJson(paired).noSpaces))
        .map(v => _resolve(Map(P.declaration.name -> v)))
      val invalid = malformed.flatMap(v => Vector(_configuration(v), ConfigurationValue.StringValue(v.noSpaces))
        .map(raw => _resolve(Map(P.declaration.name -> raw)))) ++ Vector(
        P.validateC(Some(paired.copy(conflict = null)), None, None),
        P.validateC(Some(paired.copy(conflict = Some(null))), None, None),
        P.validateC(Some(explicit.copy(conflict = Some(classifications.repair.copy(complexity = null)))), None, None))
      Then("absence remains None, explicit object and string agree, and every consumed malformed conflict refuses")
      paired.conflict shouldBe None
      classificationJson(paired).hcursor.downField("conflict").focus shouldBe None
      old.foreach(v => take(v) shouldBe Some(paired))
      valid.foreach(v => take(v) shouldBe Some(explicit))
      invalid.foreach(_.isSuccess shouldBe false)
    }
    "bind fresh conflict semantics and canonical placement without changing original obligations" in {
      Given("a fresh non-review conflict requirement, old paired configuration and an explicit STANDARD PROGRAMMING SMALL conflict")
      val original = _work(false)
      val explicit = classifications.copy(conflict = Some(classifications.repair))
      val alternate = explicit.copy(conflict = explicit.conflict.map(_.copy(complexity = Complexity.COMPLEX, domain = Domain.ENGINEERING)))
      val missing = take(WorkflowExecutionProfileMapping.createC("spec-conflict-missing", "1", Vector(profile),
        Vector(WorkflowExecutionProfileMapping.ClassBinding("ReviewStandard", profile.identity))))
      When("the fresh adapter consumes absent, logical-only and canonical mapped conflict input")
      val legacy = take(P.bindIssuedC(original, P.Work.Conflict, None, None, None))
      val paired = take(P.bindIssuedC(original, P.Work.Conflict, Some(classifications), None, None))
      val logical = take(P.bindIssuedC(original, P.Work.Conflict, Some(explicit), None, None))
      val deep = take(P.bindIssuedC(original, P.Work.Conflict, Some(alternate), None, None))
      val selected = take(P.bindIssuedC(original, P.Work.Conflict, Some(explicit), Some(mapping), Some(origin)))
      val isolated = take(P.bindIssuedC(original, P.Work.Conflict, Some(explicit), Some(mapping),
        Some(origin.copy(preference = WorkflowExecutionPreference(isolateContext = true)))))
      val refused = Vector(P.bindIssuedC(original, P.Work.Conflict, None, Some(mapping), Some(origin)),
        P.bindIssuedC(original, P.Work.Conflict, Some(classifications), Some(mapping), Some(origin)),
        P.bindIssuedC(original, P.Work.Conflict, Some(explicit), Some(missing), Some(origin)),
        P.bindIssuedC(_work(true), P.Work.Conflict, Some(explicit), None, None),
        P.bindIssuedC(selected, P.Work.Conflict, Some(explicit), Some(mapping), Some(origin)),
        P.bindIssuedC(selected, P.Work.Conflict, None, None, None))
      Then("old configurations preserve the conflict, explicit CODING preserves risk capabilities and false review obligation, and CNCF owns placement")
      legacy shouldBe original
      paired shouldBe original
      logical.handle shouldBe original.handle
      _order(logical).request shouldBe _order(original).request
      _order(logical).presentation shouldBe _order(original).presentation
      _order(logical).requirement.copy(reasoningClass = None) shouldBe _order(original).requirement
      _order(logical).requirement.reasoningClass shouldBe Some(ReasoningClassReference("CodingProgrammingStandard"))
      _order(deep).requirement.reasoning shouldBe ReasoningLevel.Deep
      _order(deep).requirement.reasoningClass shouldBe Some(ReasoningClassReference("CodingEngineeringDeep"))
      _order(selected).requirement.copy(disposition = None) shouldBe _order(logical).requirement
      _order(selected).requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Inline,
        ExecutionIndependence.Optional, origin.current.contextIdentity))
      _order(isolated).requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Delegated,
        ExecutionIndependence.Optional, origin.current.contextIdentity))
      refused.foreach(_.isSuccess shouldBe false)
    }
  }
}
