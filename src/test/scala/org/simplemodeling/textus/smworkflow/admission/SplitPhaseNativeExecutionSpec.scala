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
import org.simplemodeling.textus.smworkflow.caller.NativeWorkflowCallerExecution
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseWorkClassification.*
import SplitPhaseNativeExecution as P
import SplitPhaseExplicitPartitionFixture.take

/** Shared specification inputs only: no installed provider or authority.
  * @since   Oct. 16, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object SplitExecutionFixture {
  val profile = WorkflowConcreteExecutionProfile("spec-split-profile", "spec-provider", "spec-model", Map("effort" -> "bounded"))
  val origin = GoalPhaseNativeExecution.Origin(WorkflowExecutorDescriptor("spec-split-origin", profile), WorkflowExecutionPreference())
  val classifications = P.Classifications(Classification(Kind.DESIGN, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL),
    Classification(Kind.DESIGN, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL),
    Classification(Kind.ANALYSIS, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL))
  def classificationJson(value: P.Classifications): Json = Json.obj("schemaVersion" -> Json.fromString(P.schemaVersion),
    "partition" -> value.partition.asJson, "scope" -> value.scope.asJson, "reasoning" -> value.reasoning.asJson)
  private def _profile_json(value: WorkflowConcreteExecutionProfile): Json = Json.obj(
    "identity" -> Json.fromString(value.identity), "provider" -> Json.fromString(value.provider), "model" -> Json.fromString(value.model),
    "controls" -> Json.obj(value.controls.toVector.map((key, text) => key -> Json.fromString(text))*))
  def parameters(replacement: Boolean = false): Map[String, String] = {
    val selected = if (replacement) classifications.copy(partition = classifications.partition.copy(complexity = Complexity.COMPLEX)) else classifications
    val current = if (replacement) profile.copy(identity = "spec-later-profile", model = "spec-later-model") else profile
    val mapping = Json.obj("schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
      "identity" -> Json.fromString(if (replacement) "spec-later-mapping" else "spec-split-mapping"),
      "revision" -> Json.fromString(if (replacement) "2" else "1"), "profiles" -> Json.arr(_profile_json(current)),
      "classes" -> Json.fromValues(Vector(if (replacement) "DesignDeep" else "DesignStandard", "AnalysisStandard")
        .map(name => Json.obj("reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(current.identity)))))
    val from = Json.obj("schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
      "current" -> Json.obj("contextIdentity" -> Json.fromString(if (replacement) "spec-later-origin" else origin.current.contextIdentity),
        "profile" -> _profile_json(current)), "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.False))
    Map(P.declaration.name -> classificationJson(selected).noSpaces, WorkflowExecutionProfileParameters.declaration.name -> mapping.noSpaces,
      GoalPhaseNativeExecution.declaration.name -> from.noSpaces)
  }
  val mapping = take(WorkflowExecutionProfileParameters.decodeC(take(io.circe.parser.parse(
    parameters()(WorkflowExecutionProfileParameters.declaration.name)).fold(e => Consequence.stateConflict(e.message), Consequence.success))))
  def evidence(prepared: NativeWorkflowCallerExecution.Prepared, reference: ContextReference, worker: String): ExecutionEvidence = {
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

/** Finite Split inputs and canonical fresh selection; specification executors only.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseNativeExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitExecutionFixture.*
  private val _handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("SpecSplit"),
    WorkflowDefinitionRevision("1"), InstanceIdentity("spec-split"))
  private val _request = ContinuationRequest(StateMachineRunIdentity("spec-run"), ContinuationIdentity("spec-work"),
    StateMachineRevision("2"), StateMachineRequiredOperationIdentity("semantic-work"), StateMachineOperationIdentity("Spec", "work"),
    Some(TypedValue("SpecInput", "assigned")), Some(StateMachineResultTypeReference("SpecResult")),
    ContextBundle("Assigned", Vector.empty, Vector.empty, ContextSnapshot("1")), CompletionContract("spec-completion", Vector.empty),
    EvidenceContract("spec-evidence", Vector.empty), StateMachineOperationIdentity("Spec", "submit"))
  private def _work(review: Boolean): WorkflowInteraction[String, Nothing] = WorkflowInteraction(_handle,
    WorkflowContinuation.WorkOrder(_request, ExecutionRequirement(Vector(CapabilityRequirement("semantic-work")),
      RiskLevel("SPLIT_DESIGN"), ReasoningLevel.Standard, review), MinimalPresentation("Assigned", "Pending")))
  private def _order(value: WorkflowInteraction[String, Nothing]) = value.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
  private def _configuration(value: Json): ConfigurationValue = value.fold(ConfigurationValue.NullValue,
    ConfigurationValue.BooleanValue.apply, n => ConfigurationValue.NumberValue(n.toBigDecimal.get), ConfigurationValue.StringValue.apply,
    xs => ConfigurationValue.ListValue(xs.map(_configuration).toList),
    obj => ConfigurationValue.ObjectValue(obj.toMap.view.mapValues(_configuration).toMap))
  private def _resolve(values: Map[String, ConfigurationValue]): Consequence[Option[P.Classifications]] = {
    val id = SmWorkflowComponent.componentId
    val creation = ComponentCreate(RuntimeBindingAdmissionFixture.admit(Subsystem(name = "spec-split-execution", version = None,
      configuration = ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty), aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command)), ComponentOrigin.Builtin, Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))))
    var selected = Option.empty[P.Classifications]
    new SmWorkflowConfiguredComponentFactory(Vector(P.declaration), Vector.empty, (_, parameters, _) => P.resolveC(parameters).map { value =>
      selected = value
      Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
    }).primaryFactory.createPrimaryC(creation).map(_ => selected)
  }

  "Split semantic input" should afterWord("in spec:split-phase-native-execution, example:E1, rules:R1,R2") {
    "resolve absent object and string input and reject every malformed consumed member" in {
      Given("explicit DESIGN, DESIGN, ANALYSIS input with inert diagnostics and model/provider hints")
      val value = classificationJson(classifications).deepMerge(Json.obj("diagnostics" -> Json.True,
        "model" -> Json.True, "provider" -> Json.obj("grant" -> Json.True)))
      val members = Vector("partition", "scope", "reasoning")
      val bad = Vector(Json.Null, value.mapObject(_.remove("schemaVersion")), value.mapObject(_.add("schemaVersion", Json.Null)),
        value.mapObject(_.add("schemaVersion", Json.fromString("wrong")))) ++ members.flatMap { member =>
          val entry = value.hcursor.downField(member).focus.get
          Vector(value.mapObject(_.remove(member)), value.mapObject(_.add(member, Json.Null)),
            value.mapObject(_.add(member, entry.mapObject(_.add("kind", Json.fromString("CODING")))))) ++
            Vector("kind", "complexity", "domain", "footprint").flatMap { key =>
              Vector(value.mapObject(_.add(member, entry.mapObject(_.remove(key)))),
                value.mapObject(_.add(member, entry.mapObject(_.add(key, Json.Null)))),
                value.mapObject(_.add(member, entry.mapObject(_.add(key, Json.fromString("UNKNOWN"))))))
            }
        }
      When("ordinary Component resolution consumes both explicit representations and malformed input")
      val absent = _resolve(Map.empty)
      val valid = Vector(_configuration(value), ConfigurationValue.StringValue(value.noSpaces)).map(v => _resolve(Map(P.declaration.name -> v)))
      val rejected = bad.map(v => _resolve(Map(P.declaration.name -> _configuration(v)))) ++ Vector(
        _resolve(Map(P.declaration.name -> ConfigurationValue.StringValue("not-json"))),
        _resolve(Map(P.declaration.name -> ConfigurationValue.BooleanValue(true))), P.decodeC(null), P.resolveC(null),
        P.validateC(null, None, None), P.validateC(Some(null), None, None),
        P.validateC(Some(classifications.copy(partition = null)), None, None),
        P.validateC(Some(classifications.copy(scope = null)), None, None),
        P.validateC(Some(classifications.copy(reasoning = null)), None, None),
        P.validateC(Some(classifications.copy(scope = classifications.scope.copy(domain = null))), None, None))
      Then("absence has no defaults, hints are inert and all malformed consumed members fail")
      take(absent) shouldBe None
      valid.foreach(v => take(v) shouldBe Some(classifications))
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }
  "Split semantic derivation" should afterWord("in spec:split-phase-native-execution, example:E2, rules:R1,R2") {
    "preserve complete original work and independent review across each finite work and intensity" in {
      Given("each finite work, both original review obligations and all existing nonreview semantic hints")
      val cases = for {
        kind <- P.Work.values.toVector
        review <- Vector(false, true)
        complexity <- Complexity.values.toVector
        domain <- Domain.values.toVector
        footprint <- Footprint.values.toVector
      } yield (kind, review, complexity, domain, footprint)
      When("fresh logical-only Split binding applies the shared semantic calculation")
      val outcomes = cases.map { (kind, review, complexity, domain, footprint) =>
        val semantic = Classification(if (kind == P.Work.Reasoning) Kind.ANALYSIS else Kind.DESIGN, complexity, domain, footprint)
        val inputs = kind match {
          case P.Work.Partition => classifications.copy(partition = semantic)
          case P.Work.Scope => classifications.copy(scope = semantic)
          case P.Work.Reasoning => classifications.copy(reasoning = semantic)
        }
        val before = _work(review)
        (before, take(P.bindIssuedC(before, kind, Some(inputs), None, None)), semantic)
      }
      Then("only reasoning level and class change, with no cleared review flag or changed request or presentation")
      outcomes.foreach { (before, after, semantic) =>
        val level = semantic.complexity match {
          case Complexity.TRIVIAL | Complexity.SIMPLE => ReasoningLevel.Routine
          case Complexity.STANDARD => ReasoningLevel.Standard
          case Complexity.COMPLEX => ReasoningLevel.Deep
        }
        val requirement = _order(after).requirement
        requirement.reasoning shouldBe level
        requirement.reasoningClass shouldBe Some(ReasoningClassReference(
          semantic.kind.toString.toLowerCase.capitalize + level.value.toLowerCase.capitalize))
        after shouldBe before.copy(current = _order(before).copy(requirement = requirement))
        requirement.copy(reasoning = _order(before).requirement.reasoning, reasoningClass = _order(before).requirement.reasoningClass) shouldBe
          _order(before).requirement
      }
    }
  }
  "Canonical Split selection" should afterWord("in spec:split-phase-native-execution, example:E3, rules:R2,R3") {
    "retain independent delegated selection and refuse missing mappings pairs and malformed work" in {
      Given("a canonical same-profile mapping and origin, every finite work and both review obligations")
      val missing = take(WorkflowExecutionProfileMapping.createC("spec-partial", "1", Vector(profile),
        Vector(WorkflowExecutionProfileMapping.ClassBinding("OtherClass", profile.identity))))
      When("the adapter delegates placement to CNCF for fresh work and refuses incomplete input")
      val selected = for { kind <- P.Work.values.toVector; review <- Vector(false, true) } yield
        (review, take(P.bindIssuedC(_work(review), kind, Some(classifications), Some(mapping), Some(origin))))
      val isolated = take(P.bindIssuedC(_work(false), P.Work.Scope, Some(classifications), Some(mapping),
        Some(origin.copy(preference = WorkflowExecutionPreference(isolateContext = true)))))
      val malformed = _work(true).copy(current = _order(_work(true)).copy(requirement = null))
      val invalidbase = _work(true).copy(current = _order(_work(true)).copy(requirement =
        _order(_work(true)).requirement.copy(reasoning = null)))
      val failures = P.Work.values.toVector.flatMap(kind => Vector(
        P.bindIssuedC(_work(true), kind, Some(classifications), Some(missing), Some(origin)),
        P.bindIssuedC(_work(true), kind, None, Some(mapping), Some(origin)))) ++ Vector(
        P.bindIssuedC(_work(true), null, Some(classifications), Some(mapping), Some(origin)),
        P.bindIssuedC[String](null, P.Work.Scope, Some(classifications), None, None),
        P.bindIssuedC(malformed, P.Work.Scope, Some(classifications), None, None),
        P.bindIssuedC(invalidbase, P.Work.Scope, Some(classifications), None, None),
        P.validateC(Some(classifications), Some(mapping), None), P.validateC(Some(classifications), None, Some(origin)),
        P.validateC(Some(classifications), null, None), P.validateC(Some(classifications), None, null),
        P.validateC(Some(classifications), Some(null), Some(origin)), P.validateC(Some(classifications), Some(mapping), Some(null)),
        P.validateC(Some(classifications), Some(mapping), Some(origin.copy(current = null))),
        P.validateC(Some(classifications), Some(mapping), Some(origin.copy(preference = null))))
      Then("same-profile optional work is INLINE and independently reviewed work stays DELEGATED/REQUIRED with no fallback")
      selected.foreach { (review, work) =>
        _order(work).requirement.reviewRequired shouldBe review
        _order(work).requirement.disposition shouldBe Some(ExecutionDisposition(
          if (review) ExecutionPlacement.Delegated else ExecutionPlacement.Inline,
          if (review) ExecutionIndependence.Required else ExecutionIndependence.Optional, origin.current.contextIdentity))
      }
      _order(isolated).requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
      failures.foreach(_.isSuccess shouldBe false)
    }
  }
  "Split legacy and retained work" should afterWord("in spec:split-phase-native-execution, example:E4, rules:R3,R4,R5,R6") {
    "preserve all-absent and logical-only work and refuse Wait or selected work rebinding even after configuration removal" in {
      Given("original unmapped work, logical-only input and a saved selected independent issue")
      val original = _work(true)
      val selected = take(P.bindIssuedC(original, P.Work.Partition, Some(classifications), Some(mapping), Some(origin)))
      val wait = WorkflowInteraction[String, Nothing](_handle, WorkflowContinuation.Wait(StateMachineRevision("2"),
        _request.context.snapshot, MinimalPresentation("Wait", "Pending")))
      When("fresh binding receives absent logical-only Wait or already selected work")
      val legacy = take(P.bindIssuedC(original, P.Work.Partition, None, None, None))
      val logical = take(P.bindIssuedC(original, P.Work.Partition, Some(classifications), None, None))
      val refused = Vector(P.bindIssuedC(selected, P.Work.Partition, Some(classifications), Some(mapping), Some(origin)),
        P.bindIssuedC(selected, P.Work.Partition, None, None, None), P.bindIssuedC(wait, P.Work.Scope, None, None, None))
      Then("legacy is unchanged, logical derivation preserves review without disposition, and saved selection cannot be cleared")
      legacy shouldBe original
      _order(logical).requirement.reasoningClass shouldBe Some(ReasoningClassReference("DesignStandard"))
      _order(logical).requirement.reviewRequired shouldBe true
      _order(logical).requirement.disposition shouldBe None
      refused.foreach(_.isSuccess shouldBe false)
    }
  }
}
