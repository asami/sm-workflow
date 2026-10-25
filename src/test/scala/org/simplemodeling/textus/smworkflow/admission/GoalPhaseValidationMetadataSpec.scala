package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.Path
import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.processexecution.{SbtOperation, SbtScenarioSelection, SbtTarget, SbtScenarioParameters}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.metafixture.SelectedMetadataFixtureSpec
import org.simplemodeling.textus.smworkflow.admission.typedmetafixture.{TypedMetadataFixture as T, TypedMetadataDeclarations}
import GoalPhaseValidationMetadata as V

/** Actual local metadata discovery and bounded exact selection, without execution.
  * @since   Oct. 18, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseValidationMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _fixture_class = classOf[SelectedMetadataFixtureSpec]
  private val _loader = _fixture_class.getClassLoader
  private val _workspace = _take(WorkspaceId.parseC("sm-workflow-metadata-fixture"))
  private val _feature_a = M.FeatureReference("component:sm-workflow-fixture", "feature-a")
  private val _feature_b = M.FeatureReference("component:sm-workflow-fixture", "feature-b")
  private val _smoke_query = D.SelectionQuery(Set(M.Purpose.SMOKE))
  private val _unannotated_name = "metadata input should retain unannotated supplement"

  "Goal Phase validation metadata" should {
    "discover the isolated typed registration and bind producer defaults before deduplicating candidates" in {
      Given("the typed fixture's actual code source and loader with a query legacy supplement and equal explicit defaults")
      val request = V.Request(T.scope, D.SelectionQuery(), Vector(T.reference), _workspace,
        Vector(V.Invocation(T.reference, Map("count" -> Json.fromInt(1), "enabled" -> Json.True,
          "label" -> Json.fromString("default case")))))
      When("actual discovery resolves the schema and binds every candidate before normalized deduplication")
      val selected = _take(V.discoverC(request, T.loader))
      Then("the one real registration and its exact typed defaults produce one runnable intent")
      selected.inventory.completeness shouldBe D.Completeness.COMPLETE
      selected.inventory.operations.size shouldBe 1
      selected.inventory.operations.head.selector.suiteClassName shouldBe T.fixtureClass.getName
      selected.inventory.operations.head.metadata.get.inputSchema shouldBe new TypedMetadataDeclarations().declaration.inputSchema.get
      selected.operations.size shouldBe 1
      selected.operations.head.operation.reference shouldBe T.reference
      selected.operations.head.parameters shouldBe Map("count" -> SbtScenarioParameters.Value.Integer(1L),
        "enabled" -> SbtScenarioParameters.Value.BooleanValue(true), "label" -> SbtScenarioParameters.Value.Text("default case"))
      _take(selected.requireRunnableC) shouldBe selected
    }

    "retain two same-reference variants with global inputs and explicit invocation precedence" in {
      Given("actual typed discovery with global values and one invocation overriding only count")
      val request = V.Request(T.scope, D.SelectionQuery(), Vector.empty, _workspace,
        Vector(V.Invocation(T.reference, Map("count" -> Json.fromInt(7)))),
        Map("count" -> Json.fromInt(3), "enabled" -> Json.False, "label" -> Json.fromString("shared case")))
      When("the producer binds the query candidate followed by the explicit invocation")
      val selected = _take(V.discoverC(request, T.loader))
      Then("both exact typed variants survive in order with per-invocation count winning and globals preserved")
      selected.operations.map(_.operation.reference) shouldBe Vector.fill(2)(T.reference)
      selected.operations.map(_.parameters("count")) shouldBe Vector(SbtScenarioParameters.Value.Integer(3L),
        SbtScenarioParameters.Value.Integer(7L))
      selected.operations.foreach { operation =>
        operation.parameters("enabled") shouldBe SbtScenarioParameters.Value.BooleanValue(false)
        operation.parameters("label") shouldBe SbtScenarioParameters.Value.Text("shared case")
        operation.intent.target.scenario.get.parameters shouldBe operation.parameters
      }
    }

    "propagate actual producer refusals for wrong kinds unknown global keys explicit null and missing required inputs" in {
      Given("a real typed inventory plus supplied bad values and an explicitly negative required-without-default schema")
      val request = V.Request(T.scope, D.SelectionQuery(), Vector.empty, _workspace)
      val actual = _take(V.discoverC(request, T.loader)).inventory
      val malformed = Vector(Map("count" -> Json.fromString("2")), Map("enabled" -> Json.fromInt(1)),
        Map("label" -> Json.True), Map("unknown" -> Json.fromInt(1)), Map("count" -> Json.Null))
      val required = actual.copy(operations = actual.operations.map(operation => operation.copy(metadata = operation.metadata.map(m =>
        m.copy(inputSchema = m.inputSchema.map(p => if (p.name == "count") p.copy(defaultValue = None) else p))))))
      When("selection passes every supplied value unchanged to the actual producer binder")
      val refusals = malformed.flatMap(parameters => Vector(
        V.selectC(request.copy(executionParameters = parameters), actual),
        V.selectC(request.copy(invocations = Vector(V.Invocation(T.reference, parameters))), actual))) :+
        V.selectC(request, required)
      Then("every case fails with the producer input policy before any executable intent can be returned")
      refusals.map(_policy).distinct shouldBe Vector(Some("validation.inputs"))
      refusals.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "bound distinct normalized variants while deduplicating repeated invocations within input bounds" in {
      Given("one actual typed registration and at most sixteen explicitly supplied invocations")
      val request = V.Request(T.scope, D.SelectionQuery(), Vector.empty, _workspace)
      val actual = _take(V.discoverC(request, T.loader)).inventory
      val variants = (2 to 17).toVector.map(count => V.Invocation(T.reference, Map("count" -> Json.fromInt(count))))
      When("selection sees sixteen total variants seventeen total variants and sixteen duplicate default invocations")
      val sixteen = _take(V.selectC(request.copy(invocations = variants.take(15)), actual))
      val seventeen = V.selectC(request.copy(invocations = variants), actual)
      val duplicates = _take(V.selectC(request.copy(invocations = Vector.fill(16)(
        V.Invocation(T.reference, Map("count" -> Json.fromInt(1))))), actual))
      Then("only the final distinct bound count controls the output bound while admitted duplicates collapse")
      sixteen.operations.size shouldBe 16
      sixteen.operations.map(_.parameters("count")) shouldBe (1L to 16L).toVector.map(SbtScenarioParameters.Value.Integer(_))
      seventeen shouldBe a[Consequence.Failure[?]]
      duplicates.operations.size shouldBe 1
      _take(duplicates.requireRunnableC) shouldBe duplicates
    }

    "reject structurally missing or oversized invocation maps and vectors before producer binding" in {
      Given("a valid typed request and malformed JVM members or oversized finite inputs")
      val request = V.Request(T.scope, D.SelectionQuery(), Vector.empty, _workspace)
      val actual = _take(V.discoverC(request, T.loader)).inventory
      val invocation = V.Invocation(T.reference, Map.empty)
      val badmaps = Vector(null, Map(null.asInstanceOf[String] -> Json.True), Map("count" -> null.asInstanceOf[Json]),
        Map("count" -> Json.arr(null.asInstanceOf[Json])),
        Map("count" -> Json.obj("nested" -> null.asInstanceOf[Json])),
        (1 to 17).map(index => s"key-$index" -> Json.True).toMap)
      val malformed = Vector(request.copy(invocations = null), request.copy(invocations = Vector(null)),
        request.copy(invocations = Vector(invocation.copy(reference = null))),
        request.copy(invocations = Vector.fill(17)(invocation))) ++ badmaps.flatMap(parameters => Vector(
          request.copy(executionParameters = parameters), request.copy(invocations = Vector(invocation.copy(parameters = parameters)))))
      When("both helper entry points receive these invalid structural requests")
      val refusals = malformed.flatMap(value => Vector(V.selectC(value, actual), V.discoverC(value, T.loader)))
      Then("all fail as structured requests without silently omitting null members or admitting oversized input")
      refusals.size shouldBe malformed.size * 2
      refusals.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "discover three real registrations and apply purpose OR with feature AND" in {
      Given("the compiled fixture's own code source, actual loader and an exact narrow package scope")
      val request = _request(D.SelectionQuery(Set(M.Purpose.SMOKE, M.Purpose.FOCUSED), Set(_feature_a)))
      When("the helper delegates actual discovery and producer query selection")
      val selected = _take(V.discoverC(request, _loader))
      val inventory = selected.inventory
      Then("COMPLETE inventory retains all three real names and the combined query selects smoke-a only")
      inventory.completeness shouldBe D.Completeness.COMPLETE
      inventory.scope shouldBe request.scope
      inventory.diagnostics shouldBe empty
      inventory.operations.size shouldBe 3
      inventory.operations.map(_.selector.testName).toSet shouldBe Set(
        "metadata input should run smoke feature a", "metadata input should run focused feature b", _unannotated_name)
      selected.operations.map(_.operation.reference.operation) shouldBe Vector("smoke-a")
      inventory.operations.foreach { operation =>
        operation.reference.component shouldBe "component:sm-workflow-fixture"
        operation.reference.specification shouldBe "metadata-fixture"
        operation.selector.suiteClassName shouldBe _fixture_class.getName
        operation.provenance shouldBe D.Provenance("sm-workflow-fixture-tests", "artifact-1", "source-1", "1")
        operation.diagnostics shouldBe empty
        operation.metadata should not be empty
      }
    }

    "leave empty query dimensions unfiltered and retain exact feature scopes" in {
      Given("real discovery containing smoke, focused and unannotated registrations")
      val request = _request()
      val discovered = _take(V.discoverC(request, _loader))
      When("empty, purpose-only, feature-only and feature-union queries use that actual inventory")
      val all = _take(V.selectC(request, discovered.inventory))
      val purposes = _take(V.selectC(request.copy(query = D.SelectionQuery(Set(M.Purpose.SMOKE, M.Purpose.FOCUSED))), discovered.inventory))
      val feature = _take(V.selectC(request.copy(query = D.SelectionQuery(features = Set(_feature_a))), discovered.inventory))
      val features = _take(V.selectC(request.copy(query = D.SelectionQuery(features = Set(_feature_a, _feature_b))), discovered.inventory))
      val other = _take(V.selectC(request.copy(query = D.SelectionQuery(features = Set(_feature_a.copy(scope = "another:scope")))), discovered.inventory))
      Then("empty dimensions add no filter, features use OR and equal labels in another scope do not match")
      all.operations.map(_.operation) shouldBe discovered.inventory.operations
      purposes.operations.map(_.operation.reference.operation).toSet shouldBe Set("smoke-a", "focused-b")
      feature.operations.map(_.operation.reference.operation) shouldBe Vector("smoke-a")
      features.operations.map(_.operation.reference.operation).toSet shouldBe Set("smoke-a", "focused-b")
      other.operations shouldBe empty
    }

    "deduplicate the query and supplements while retaining unchanged producer intent and declarations" in {
      Given("actual smoke selection and explicit focused, unannotated and repeated logical references")
      val request = _request(_smoke_query)
      val discovered = _take(V.discoverC(request, _loader))
      val smoke = discovered.inventory.operations.find(_.reference.operation == "smoke-a").get
      val focused = discovered.inventory.operations.find(_.reference.operation == "focused-b").get
      val unannotated = discovered.inventory.operations.find(_.selector.testName == _unannotated_name).get
      val supplemental = Vector(focused.reference, smoke.reference, unannotated.reference, focused.reference)
      When("the helper appends exact supplements and binds the first occurrence of each reference")
      val selected = _take(V.selectC(request.copy(supplemental = supplemental), discovered.inventory))
      Then("query order leads the union and every unchanged producer operation has only its exact TestOnly intent")
      (selected.inventory eq discovered.inventory) shouldBe true
      selected.query shouldBe request.query
      selected.supplemental shouldBe supplemental
      selected.operations.map(_.operation) shouldBe Vector(smoke, focused, unannotated)
      selected.operations.zip(Vector(smoke, focused, unannotated)).foreach { case (bound, original) =>
        (bound.operation eq original) shouldBe true
        bound.operation.reference shouldBe original.reference
        bound.operation.provenance shouldBe original.provenance
        bound.operation.metadata shouldBe original.metadata
        bound.intent shouldBe UnitOfWorkOp.SbtExec(SbtTarget(_workspace,
          Some(SbtScenarioSelection(original.selector.suiteClassName, original.selector.testName))), SbtOperation.TestOnly)
      }
      focused.metadata.get.purposes shouldBe Set(M.Purpose.FOCUSED)
      focused.metadata.get.features shouldBe Set(_feature_b)
      focused.metadata.get.expectedDuration shouldBe Some(500.millis)
      focused.metadata.get.durationClass shouldBe M.DurationClass.NORMAL
      focused.metadata.get.executionRequirement shouldBe Some(ExecutionRequirement(
        Vector(CapabilityRequirement("metadata-fixture")), RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false))
      focused.metadata.get.origins shouldBe M.Origins(true, true)
      unannotated.metadata.get.purposes shouldBe empty
      unannotated.metadata.get.features shouldBe empty
      unannotated.metadata.get.executionRequirement shouldBe None
      unannotated.metadata.get.origins shouldBe M.Origins(true, false)
    }

    "represent an empty complete selection and admit only a nonempty complete batch" in {
      Given("a real COMPLETE inventory and a valid query whose feature is absent from its declarations")
      val request = _request(_smoke_query)
      val discovered = _take(V.discoverC(request, _loader))
      val unmatched = request.copy(query = D.SelectionQuery(features = Set(_feature_a.copy(identity = "undeclared-feature"))))
      When("the unmatched query is represented and both selections request runnable eligibility")
      val empty = _take(V.selectC(unmatched, discovered.inventory))
      val refused = empty.requireRunnableC
      val runnable = _take(discovered.requireRunnableC)
      Then("empty remains explicit and fails while the actual nonempty COMPLETE selection returns itself")
      empty.operations shouldBe Vector.empty
      (empty.inventory eq discovered.inventory) shouldBe true
      empty.inventory.completeness shouldBe D.Completeness.COMPLETE
      refused shouldBe a[Consequence.Failure[?]]
      refused.toString should include (s"scope=${request.scope.identity}")
      refused.toString should include (s"revision=${request.scope.revision}")
      refused.toString should include ("completeness=COMPLETE")
      (runnable eq discovered) shouldBe true
    }

    "reject runnable eligibility for an explicitly synthetic PARTIAL inventory even with a selection" in {
      Given("real discovered rows and a synthetic PARTIAL negative variant with an enumeration diagnostic")
      val request = _request(_smoke_query)
      val actual = _take(V.discoverC(request, _loader)).inventory
      val diagnostic = D.Diagnostic("enumeration", "partial-fixture", "Synthetic negative completeness input")
      val partial = actual.copy(completeness = D.Completeness.PARTIAL, diagnostics = Vector(diagnostic))
      When("the helper retains the PARTIAL inventory and checks selected-batch eligibility")
      val selected = _take(V.selectC(request, partial))
      val refused = selected.requireRunnableC
      Then("nonempty selection cannot prove a required batch and actual partial scope context remains visible")
      selected.operations.map(_.operation.reference.operation) shouldBe Vector("smoke-a")
      (selected.inventory eq partial) shouldBe true
      selected.inventory.completeness shouldBe D.Completeness.PARTIAL
      selected.inventory.diagnostics shouldBe Vector(diagnostic)
      refused shouldBe a[Consequence.Failure[?]]
      refused.toString should include (s"scope=${request.scope.identity}")
      refused.toString should include (s"revision=${request.scope.revision}")
      refused.toString should include ("completeness=PARTIAL")
      refused.toString should include (diagnostic.message)
    }

    "reject runnable eligibility for an explicitly synthetic UNKNOWN inventory without proving absence" in {
      Given("real discovered rows and a synthetic UNKNOWN negative variant with a loading diagnostic")
      val request = _request(_smoke_query)
      val actual = _take(V.discoverC(request, _loader)).inventory
      val diagnostic = D.Diagnostic("loading", "unknown-fixture", "Synthetic unknown completeness input")
      val unknown = actual.copy(completeness = D.Completeness.UNKNOWN, diagnostics = Vector(diagnostic))
      When("both matching and unmatched queries retain UNKNOWN context before eligibility checks")
      val selected = _take(V.selectC(request, unknown))
      val unmatched = _take(V.selectC(request.copy(query = D.SelectionQuery(features = Set(_feature_a.copy(identity = "undeclared-feature")))), unknown))
      val refusals = Vector(selected.requireRunnableC, unmatched.requireRunnableC)
      Then("neither nonempty nor empty UNKNOWN selection satisfies a required batch or loses its diagnostics")
      selected.operations should not be empty
      unmatched.operations shouldBe empty
      Vector(selected, unmatched).foreach { value =>
        (value.inventory eq unknown) shouldBe true
        value.inventory.completeness shouldBe D.Completeness.UNKNOWN
        value.inventory.diagnostics shouldBe Vector(diagnostic)
      }
      refusals.foreach { refusal =>
        refusal shouldBe a[Consequence.Failure[?]]
        refusal.toString should include (s"scope=${request.scope.identity}")
        refusal.toString should include (s"revision=${request.scope.revision}")
        refusal.toString should include ("completeness=UNKNOWN")
        refusal.toString should include (diagnostic.message)
      }
    }

    "accept the producer's artifact sorting while retaining all exact scope fields" in {
      Given("two explicit component/artifact scopes over the same real narrow fixture package in reverse order")
      val base = _request(_smoke_query)
      val first = base.scope.artifacts.head
      val last = first.copy(componentIdentity = "component:z-fixture", artifactIdentity = "z-fixture-tests")
      val request = base.copy(scope = base.scope.copy(artifacts = Vector(last, first)))
      When("actual discovery applies the producer's documented component/artifact sort")
      val selected = _take(V.discoverC(request, _loader))
      Then("the sorted actual scope is accepted without changing revisions, artifacts or logical selections")
      selected.inventory.scope shouldBe request.scope.copy(artifacts = Vector(first, last))
      selected.inventory.completeness shouldBe D.Completeness.COMPLETE
      selected.inventory.operations.size shouldBe 6
      selected.operations.map(_.operation.reference.component) shouldBe Vector(first.componentIdentity, last.componentIdentity)
      _take(selected.requireRunnableC) shouldBe selected
    }

    "reject a request differing in any exact scope identity revision or artifact field" in {
      Given("a real inventory and requests differing individually in scope or artifact context")
      val request = _request()
      val actual = _take(V.discoverC(request, _loader)).inventory
      val artifact = request.scope.artifacts.head
      val artifacts = Vector(artifact.copy(componentIdentity = "component:other"),
        artifact.copy(artifactIdentity = "other-artifact"), artifact.copy(artifactRevision = "artifact-2"),
        artifact.copy(sourceRevision = "source-2"), artifact.copy(root = artifact.root.resolve("other")),
        artifact.copy(packagePrefix = artifact.packagePrefix + ".other"))
      val scopes = Vector(request.scope.copy(identity = "other-scope"), request.scope.copy(revision = "scope-2"),
        request.scope.copy(artifacts = Vector.empty)) ++ artifacts.map(x => request.scope.copy(artifacts = Vector(x)))
      When("each changed request tries to select from the unchanged actual inventory")
      val outcomes = scopes.map(scope => V.selectC(request.copy(scope = scope), actual))
      Then("every changed exact field fails rather than broadening or rebinding discovery context")
      outcomes.size shouldBe 9
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject missing request fields null vectors and null reference elements before discovery or binding" in {
      Given("actual inventory plus requests with missing typed fields, vector members or artifact fields")
      val request = _request()
      val actual = _take(V.discoverC(request, _loader)).inventory
      val reference = actual.operations.head.reference
      val artifact = request.scope.artifacts.head
      val malformed = Vector(null, request.copy(scope = null), request.copy(query = null),
        request.copy(supplemental = null), request.copy(workspace = null.asInstanceOf[WorkspaceId]),
        request.copy(supplemental = Vector(null)), request.copy(supplemental = Vector(reference.copy(component = null))),
        request.copy(supplemental = Vector(reference.copy(specification = null))),
        request.copy(supplemental = Vector(reference.copy(operation = null))),
        request.copy(scope = request.scope.copy(identity = null)), request.copy(scope = request.scope.copy(revision = null)),
        request.copy(scope = request.scope.copy(artifacts = null)), request.copy(scope = request.scope.copy(artifacts = Vector(null))),
        request.copy(scope = request.scope.copy(artifacts = Vector(artifact.copy(componentIdentity = null)))),
        request.copy(scope = request.scope.copy(artifacts = Vector(artifact.copy(artifactIdentity = null)))),
        request.copy(scope = request.scope.copy(artifacts = Vector(artifact.copy(artifactRevision = null)))),
        request.copy(scope = request.scope.copy(artifacts = Vector(artifact.copy(sourceRevision = null)))),
        request.copy(scope = request.scope.copy(artifacts = Vector(artifact.copy(root = null)))),
        request.copy(scope = request.scope.copy(artifacts = Vector(artifact.copy(packagePrefix = null)))))
      When("both helper entry points receive each malformed request")
      val outcomes = malformed.flatMap(value => Vector(V.discoverC(value, _loader), V.selectC(value, actual)))
      Then("all missing inputs remain structured failures without dereferencing absent values")
      outcomes.size shouldBe malformed.size * 2
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "propagate producer query inventory and loader validation failures" in {
      Given("a real inventory, malformed producer query fields and missing inventory or caller loader")
      val request = _request()
      val actual = _take(V.discoverC(request, _loader)).inventory
      val queries = Vector(D.SelectionQuery(purposes = null), D.SelectionQuery(features = null),
        D.SelectionQuery(purposes = Set(null.asInstanceOf[M.Purpose])), D.SelectionQuery(features = Set(null)),
        D.SelectionQuery(features = Set(_feature_a.copy(scope = " invalid scope"))))
      When("the helper delegates those boundaries to the producer APIs")
      val queryoutcomes = queries.map(query => V.selectC(request.copy(query = query), actual))
      val inventoryoutcome = V.selectC(request, null)
      val loaderoutcome = V.discoverC(request, null)
      Then("producer policies remain structured and distinguish query, feature and null boundary refusals")
      queryoutcomes.map(_policy) shouldBe Vector(Some("validation.discovery.query"), Some("validation.discovery.query"),
        Some("validation.discovery.query"), Some("validation.discovery.feature"), Some("validation.discovery.feature"))
      _policy(inventoryoutcome) shouldBe Some("validation.discovery.null")
      _policy(loaderoutcome) shouldBe Some("validation.discovery.null")
    }

    "bound supplemental input and the actual deduplicated union to sixteen operations" in {
      Given("six explicit component scopes discovering eighteen real fixture registrations")
      val base = _request(D.SelectionQuery(features = Set(_feature_a.copy(identity = "undeclared-feature"))))
      val artifact = base.scope.artifacts.head
      val artifacts = (1 to 6).toVector.map(index => artifact.copy(componentIdentity = s"component:fixture-$index",
        artifactIdentity = s"fixture-tests-$index"))
      val request = base.copy(scope = base.scope.copy(artifacts = artifacts))
      val actual = _take(V.discoverC(request, _loader)).inventory
      val smoke = _take(D.selectC(actual, _smoke_query))
      val extras = actual.operations.filterNot(operation => smoke.exists(_.reference == operation.reference)).take(10)
      val bounded = request.copy(query = _smoke_query, supplemental = extras.map(_.reference))
      When("six queried operations plus ten distinct supplements, eighteen selected rows and seventeen duplicate supplements are submitted")
      val sixteen = _take(V.selectC(bounded, actual))
      val toomanyselected = V.selectC(request.copy(query = D.SelectionQuery()), actual)
      val toomanysupplemental = request.copy(supplemental = Vector.fill(17)(actual.operations.head.reference))
      val supplementalrefusals = Vector(V.selectC(toomanysupplemental, actual), V.discoverC(toomanysupplemental, _loader))
      Then("sixteen real bound operations are eligible while either input bound refuses oversized batches")
      actual.completeness shouldBe D.Completeness.COMPLETE
      actual.operations.size shouldBe 18
      sixteen.operations.map(_.operation.reference) shouldBe smoke.map(_.reference) ++ extras.map(_.reference)
      sixteen.operations.size shouldBe 16
      _take(sixteen.requireRunnableC) shouldBe sixteen
      toomanyselected shouldBe a[Consequence.Failure[?]]
      supplementalrefusals.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "preserve distinct missing invalid and ambiguous exact supplemental failures" in {
      Given("real provenance and explicit negative copies with absent metadata or a duplicated reference")
      val request = _request(D.SelectionQuery(features = Set(_feature_a.copy(identity = "undeclared-feature"))))
      val actual = _take(V.discoverC(request, _loader)).inventory
      val operation = actual.operations.find(_.reference.operation == "smoke-a").get
      val missing = operation.reference.copy(operation = "missing-exact-operation")
      val invalid = actual.copy(operations = actual.operations.map(value =>
        if (value.reference == operation.reference) value.copy(metadata = None) else value))
      val ambiguous = actual.copy(operations = actual.operations :+ operation)
      When("supplemental exact binding resolves the missing and explicitly synthetic invalid or ambiguous negative inputs")
      val outcomes = Vector(V.selectC(request.copy(supplemental = Vector(missing)), actual),
        V.selectC(request.copy(supplemental = Vector(operation.reference)), invalid),
        V.selectC(request.copy(supplemental = Vector(operation.reference)), ambiguous))
      Then("the producer's absent, invalid and ambiguous policies survive without empty-success fallback")
      outcomes.map(_policy) shouldBe Vector(Some("validation.discovery.absent"),
        Some("validation.discovery.invalid"), Some("validation.discovery.ambiguous"))
      invalid.operations.find(_.reference == operation.reference).get.provenance shouldBe operation.provenance
      ambiguous.operations.filter(_.reference == operation.reference).map(_.provenance) shouldBe Vector.fill(2)(operation.provenance)
    }
  }

  private def _request(query: D.SelectionQuery = D.SelectionQuery()): V.Request = {
    val root = Path.of(_fixture_class.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    val artifact = D.ArtifactScope("component:sm-workflow-fixture", "sm-workflow-fixture-tests", "artifact-1", "source-1",
      root, "org.simplemodeling.textus.smworkflow.admission.metafixture")
    V.Request(D.DiscoveryScope("sm-workflow-metadata-fixture", "scope-1", Vector(artifact)), query, Vector.empty, _workspace)
  }

  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Expected typed metadata selection success: $other")
  }

  private def _policy[A](result: Consequence[A]): Option[String] = result match {
    case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
    case _ => fail("Expected structured producer failure")
  }
}
