package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.validation.ValidationDiscoveryV1 as D
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.typedmetafixture.TypedMetadataFixture as T
import GoalPhaseSliceValidationPlan as L
import GoalPhaseNativeMetadata as N
import GoalPhaseNativeValidation as V
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Captured file data plus real producer fixture selection; no File/runtime proof.
  * @since   Oct. 18, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object GoalPhaseSliceValidationPlanFixture {
  import N.given
  val source = L.Source("docs/slices/S1-validation.json", "slice-validation-S1", "S1",
    Vector("smoke-condition", "focused-condition"))
  def plan: L.Plan = {
    val embedded = GoalPhaseNativeMetadataFixture.profile
    val reference = embedded.supplemental.head
    L.Plan(source.identity, "revision-1", source.sliceRef,
      Vector(L.Condition("smoke-condition", "The real smoke scenario is selected"),
        L.Condition("focused-condition", "The exact focused scenario is included")),
      embedded.query, Vector(L.Invocation(reference, Map.empty)), Map.empty,
      Vector(L.Coverage("smoke-condition", true, Vector.empty), L.Coverage("focused-condition", false, Vector(reference))))
  }
  def profile: N.Profile = GoalPhaseNativeMetadataFixture.profile.copy(query = D.SelectionQuery(),
    supplemental = Vector.empty, source = Some(source))
  def check: V.Check = GoalPhaseNativeMetadataFixture.check.copy(metadata = Some(profile))
  def envelope(plan: L.Plan): Json = Json.obj("schemaVersion" -> Json.fromString(L.schemaVersion), "payload" -> plan.asJson)
}

/** Closed Plan admission and exact producer-bound selection.
  * @since   Oct. 18, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseSliceValidationPlanSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import N.given
  import GoalPhaseSliceValidationPlanFixture.*
  private def _decode(value: Json) = L.decodeC(value.noSpaces)
  private def _payload(value: Json) = Json.obj("schemaVersion" -> Json.fromString(L.schemaVersion), "payload" -> value)

  "An explicit Slice validation plan" should {
    "retain exact source identity revision conditions and typed operation references" in {
      Given("an explicit literal source and a complete zero-input Slice plan for the real compiled fixture")
      val original = plan
      val wire = envelope(original)
      When("the exact file envelope is decoded and admitted against its selected source")
      val decoded = take(_decode(wire))
      val admitted = take(L.admitC(source, decoded))
      val restoredsource = source.asJson.as[L.Source]
      Then("all typed meaning survives including the exact configured revision and acceptance descriptions")
      decoded shouldBe original
      admitted shouldBe original
      restoredsource shouldBe Right(source)
      admitted.revision shouldBe "revision-1"
      admitted.acceptanceConditions.map(_.identity) shouldBe source.acceptanceConditions
      envelope(admitted) shouldBe wire
    }

    "refuse unknown null and missing fields at every introduced object boundary" in {
      Given("a complete Plan Source Condition Invocation and Coverage with every required array and map present")
      val original = plan
      val payload = original.asJson
      def _variants(value: Json): Vector[Json] = value.asObject.get.keys.toVector.flatMap(key =>
        Vector(value.mapObject(_.remove(key)), value.mapObject(_.add(key, Json.Null)))) :+
        value.mapObject(_.add("unknown", Json.True))
      val planvariants = _variants(payload).map(_payload)
      val nestedvariants = Vector("acceptanceConditions", "additionalOperations", "acceptanceCoverageRefs").flatMap { key =>
        val values = payload.hcursor.get[Vector[Json]](key).toOption.get
        _variants(values.head).map(value => _payload(payload.mapObject(_.add(key, Json.fromValues(values.updated(0, value))))))
      }
      val sourcevariants = _variants(source.asJson)
      val envelopevariants = _variants(envelope(original)) :+
        envelope(original).mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.slice-validation-plan.v0")))
      When("each malformed object reaches its actual closed codec or file decoder")
      val refusedplans = (planvariants ++ nestedvariants ++ envelopevariants).map(_decode)
      val refusedsources = sourcevariants.map(_.as[L.Source])
      Then("every malformed presence is refused and no absent map or array becomes an empty default")
      refusedplans.foreach(_.isSuccess shouldBe false)
      refusedsources.foreach(_.isLeft shouldBe true)
    }

    "bind only literal paths in the actual scope and both exact ownership lists" in {
      Given("the selected Step owns its explicit file and the declaration includes that same candidate path")
      val owned = Vector("src/one.scala", source.path)
      val candidates = Vector(source.path)
      val invalidpaths = Vector("file:docs/plan.json", "https://example.test/plan.json", "/tmp/plan.json",
        "docs/../plan.json", "docs//plan.json", "docs\\plan.json", "")
      When("pure source binding checks Step and Phase scopes literal filenames and the two ownership lists")
      val valid = L.bindingC(source, "S1", owned, candidates)
      val phase = L.bindingC(source.copy(sliceRef = "phase:PHASE-6.2"), "phase:PHASE-6.2", owned, candidates)
      val refused = invalidpaths.map(path => L.bindingC(source.copy(path = path), "S1", owned, candidates)) ++
        Vector(L.bindingC(source, "S2", owned, candidates), L.bindingC(source, "S1", Vector("docs/slices"), candidates),
          L.bindingC(source, "S1", owned, Vector("docs/slices")),
          L.bindingC(source.copy(acceptanceConditions = Vector(" ")), "S1", owned, candidates))
      Then("only the literal declared file and actual matching scope are admitted without opening any file")
      valid.isSuccess shouldBe true
      phase.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
    }

    "reuse strict producer query and reference codecs and bound the selected source contract" in {
      Given("a valid Slice file with producer-owned query and operation-reference fields")
      val original = plan
      val payload = original.asJson
      val query = original.metadataSelection.asJson
      val invocation = original.additionalOperations.head.asJson
      val reference = original.additionalOperations.head.reference.asJson
      val badqueries = Vector(query.mapObject(_.add("purposes", Json.arr(Json.fromString("FUTURE")))),
        query.mapObject(_.add("features", Json.Null)), query.mapObject(_.add("annotation", Json.fromString("@smoke"))))
      val badreferences = Vector(reference.mapObject(_.add("operation", Json.fromString(""))),
        reference.mapObject(_.add("component", Json.Null)), reference.mapObject(_.remove("specification")),
        reference.mapObject(_.add("argv", Json.arr())))
      val variants = badqueries.map(value => _payload(payload.mapObject(_.add("metadataSelection", value)))) ++
        badreferences.map(value => _payload(payload.mapObject(_.add("additionalOperations",
          Json.arr(invocation.mapObject(_.add("reference", value)))))))
      val sources = Vector(source.copy(identity = " "), source.copy(identity = "x" * 4097),
        source.copy(acceptanceConditions = Vector.empty),
        source.copy(acceptanceConditions = source.acceptanceConditions :+ source.acceptanceConditions.head),
        source.copy(acceptanceConditions = (1 to 17).toVector.map(index => "condition-" + index)))
      When("the file decoder and selected Source codec consume these malformed producer fields and source bounds")
      val refusedplans = variants.map(_decode)
      val refusedsources = sources.map(_.asJson.as[L.Source])
      Then("unknown producer declarations malformed exact references and invalid bounded source contracts are rejected")
      refusedplans.foreach(_.isSuccess shouldBe false)
      refusedsources.foreach(_.isLeft shouldBe true)
    }

    "refuse wrong identity slice condition sets bounds and incomplete or duplicate coverage" in {
      Given("one exact source and its two-condition plan with unique complete condition coverage")
      val original = plan
      val variants = Vector(original.copy(identity = "different"), original.copy(revision = " "),
        original.copy(sliceRef = "S2"), original.copy(acceptanceConditions = Vector.empty),
        original.copy(acceptanceConditions = original.acceptanceConditions :+ original.acceptanceConditions.head),
        original.copy(acceptanceConditions = Vector.fill(17)(original.acceptanceConditions.head)),
        original.copy(acceptanceConditions = original.acceptanceConditions.updated(0, L.Condition("unexpected", "A changed requirement"))),
        original.copy(acceptanceConditions = original.acceptanceConditions.updated(0, L.Condition("smoke-condition", " "))),
        original.copy(acceptanceCoverageRefs = original.acceptanceCoverageRefs.tail),
        original.copy(acceptanceCoverageRefs = original.acceptanceCoverageRefs :+ original.acceptanceCoverageRefs.head),
        original.copy(acceptanceCoverageRefs = original.acceptanceCoverageRefs.updated(0, L.Coverage("unknown", true, Vector.empty))),
        original.copy(acceptanceCoverageRefs = original.acceptanceCoverageRefs.updated(0, L.Coverage("smoke-condition", false, Vector.empty))))
      When("each variant is decoded through the file envelope and admitted against the original selected source")
      val refusals = variants.map(value => _decode(envelope(value)).flatMap(L.admitC(source, _)))
      Then("no unknown missing repeated blank or out-of-bounds acceptance contract replaces the selected one")
      refusals.foreach(_.isSuccess shouldBe false)
      L.admitC(source.copy(acceptanceConditions = source.acceptanceConditions.reverse), original).isSuccess shouldBe true
    }

    "require actual queried coverage and reject exact unresolved coverage or supplemental references" in {
      Given("the real COMPLETE compiled fixture inventory and its exact query-plus-supplemental selection")
      val actual = GoalPhaseNativeMetadataFixture.batch
      val original = plan
      val absent = original.additionalOperations.head.reference.copy(operation = "not-a-registration")
      val emptyquery = original.copy(metadataSelection = D.SelectionQuery(features = Set(
        org.goldenport.cncf.validation.ValidationMetadataV1.FeatureReference("component:sm-workflow-fixture", "absent"))))
      val wrongcoverage = original.copy(acceptanceCoverageRefs = original.acceptanceCoverageRefs.updated(1,
        L.Coverage("focused-condition", false, Vector(absent))))
      val wronginvocation = original.copy(additionalOperations = Vector(L.Invocation(absent, Map.empty)))
      When("coverage uses actual discovered rows and source-bound selection resolves the exact supplemental reference")
      val coverage = L.coverageC(original, actual.inventory, actual.references)
      val empty = L.coverageC(emptyquery, actual.inventory, actual.references)
      val unresolved = L.coverageC(wrongcoverage, actual.inventory, actual.references)
      val missingoperation = N.prepareC(Vector(check), GoalPhaseNativeMetadataFixture.loader, Map(check.identity -> wronginvocation))
      Then("only actual query and union members cover conditions and an unresolved exact invocation cannot be bound")
      coverage.isSuccess shouldBe true
      empty.isSuccess shouldBe false
      unresolved.isSuccess shouldBe false
      missingoperation.isSuccess shouldBe false
    }

    "admit finite supplied data and leave wrong kind unknown key and explicit null rejection to the producer" in {
      Given("the actual typed fixture's source Plan with structurally valid but semantically bad supplied values")
      val original = T.plan
      val variants = Vector(original.copy(executionParameters = Map("unknown" -> Json.True)),
        original.copy(executionParameters = Map("count" -> Json.Null)),
        original.copy(additionalOperations = Vector(L.Invocation(T.reference, Map("count" -> Json.fromString("two"))))))
      When("the exact file decoder and source admission preserve data before actual producer selection binds it")
      val admitted = variants.map(value => take(_decode(envelope(value)).flatMap(L.admitC(T.source, _))))
      val refused = admitted.map(value => N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> value)))
      Then("the Plan keeps every value including ordinary JSON null while the producer refuses every bad invocation")
      admitted shouldBe variants
      admitted(1).executionParameters("count") shouldBe Json.Null
      refused.foreach { result =>
        result.isSuccess shouldBe false
        result.toString should include("validation.inputs")
      }
    }

    "retain global inputs and distinct same-reference invocations through source binding and exact coverage" in {
      Given("an owned typed source and Plan whose query and explicit invocation use different count values")
      val original = T.plan.copy(executionParameters = Map("count" -> Json.fromInt(3), "enabled" -> Json.False,
        "label" -> Json.fromString("captured case")))
      When("the source binds its owned path and its decoded full Plan enters actual native discovery")
      val binding = L.bindingC(T.source, "S1", Vector(T.source.path), Vector(T.source.path))
      val admitted = take(_decode(envelope(original)).flatMap(L.admitC(T.source, _)))
      val (selected, batches) = take(N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> admitted)))
      Then("the captured full Plan and actual inventory retain both parameter meanings under complete coverage")
      binding.isSuccess shouldBe true
      admitted shouldBe original
      batches.head.plan shouldBe Some(original)
      batches.head.references shouldBe Vector.fill(2)(T.reference)
      selected.map(_.identity) shouldBe Vector(1, 2).map(index =>
        N.logicalIdentity(T.check.identity, T.reference) + ":invocation:" + index)
      L.coverageC(admitted, batches.head.inventory, batches.head.references).isSuccess shouldBe true
      N.validateRetainedC(Vector(T.check), selected, batches).isSuccess shouldBe true
      batches.head.asJson.as[N.Batch] shouldBe Right(batches.head)
    }

    "reject malformed JVM members and oversized parameter maps without imposing schema policy" in {
      Given("an otherwise valid Plan with null maps keys values nested JVM members or seventeen map entries")
      val original = T.plan
      val malformed = Vector(null, Map(null.asInstanceOf[String] -> Json.True), Map("count" -> null.asInstanceOf[Json]),
        Map("count" -> Json.arr(null.asInstanceOf[Json])), Map("count" -> Json.obj("nested" -> null.asInstanceOf[Json])),
        (1 to 17).map(index => s"key-$index" -> Json.True).toMap)
      val variants = malformed.flatMap(parameters => Vector(original.copy(executionParameters = parameters),
        original.copy(additionalOperations = Vector(L.Invocation(T.reference, parameters)))))
      When("typed admission checks each finite-map structure against the original source")
      val refused = variants.map(L.admitC(T.source, _))
      Then("all structurally malformed parameter maps fail while ordinary JSON null remains structurally admitted")
      refused.size shouldBe malformed.size * 2
      refused.foreach(_.isSuccess shouldBe false)
      L.admitC(T.source, original.copy(executionParameters = Map("count" -> Json.Null))).isSuccess shouldBe true
    }

    "require exact captured source groups without falling back to embedded selection" in {
      Given("a source-bound host check whose profile intentionally has no embedded query or supplemental operations")
      val selected = check
      val loader = GoalPhaseNativeMetadataFixture.loader
      When("preparation receives missing extra wrong or embedded-only captured data")
      val refusals = Vector(N.prepareC(Vector(selected), loader),
        N.prepareC(Vector(selected), loader, Map("another-host" -> plan)),
        N.prepareC(Vector(selected), loader, Map(selected.identity -> plan.copy(identity = "wrong-plan"))),
        N.prepareC(Vector(GoalPhaseNativeMetadataFixture.check), loader, Map(selected.identity -> plan)))
      Then("every mismatch fails and only the exact captured typed plan supplies source selection")
      refusals.foreach(_.isSuccess shouldBe false)
      val actual = take(N.prepareC(Vector(selected), loader, Map(selected.identity -> plan)))
      actual._2.head.plan shouldBe Some(plan)
      actual._2.head.references shouldBe GoalPhaseNativeMetadataFixture.batch.references
    }

    "deduplicate identical zero-input invocations through the existing producer-selected union" in {
      Given("the same exact supplemental invocation declared twice alongside the real smoke query")
      val original = plan
      val duplicate = original.copy(additionalOperations = original.additionalOperations ++ original.additionalOperations)
      When("the captured plan reaches the existing native discovery selection and binding route")
      val actual = take(N.prepareC(Vector(check), GoalPhaseNativeMetadataFixture.loader, Map(check.identity -> duplicate)))
      Then("the original typed declaration retains both invocations while each exact operation is selected once")
      actual._2.head.plan shouldBe Some(duplicate)
      actual._2.head.references shouldBe GoalPhaseNativeMetadataFixture.batch.references
      actual._1.size shouldBe 2
      actual._2.head.plan.get.additionalOperations.size shouldBe 2
    }

    "issue only selected Step or Phase source contracts without reading a file" in {
      Given("host policies containing a selected source and an unselected foreign-scope source for a file not yet authored")
      val selected = check
      val phasecheck = selected.copy(identity = "phase-metadata", metadata = Some(profile.copy(
        source = Some(source.copy(sliceRef = "phase:PHASE-6.2")))))
      val foreign = selected.copy(identity = "foreign-source", ordinal = 3, metadata = Some(profile.copy(
        source = Some(source.copy(path = "elsewhere.json", sliceRef = "S2")))))
      val selectedplan = O.Plan("slice-plan", "1", "PHASE-6.2", "selected-repository",
        Vector(O.Step("S1", "Author the selected contract", Vector(source.path), Vector.empty, Vector(selected.identity)),
          O.Step("S2", "Later work", Vector("elsewhere.json"), Vector("S1"), Vector(foreign.identity))), Vector(phasecheck.identity))
      // Only policy/path fields are consumed by this pure instruction projection.
      val declaration = GoalPhaseNativeRun.Declaration(null, null, null, null, null,
        candidatePaths = Vector(source.path, "elsewhere.json"),
        validationPolicy = Some(V.Policy("slice-checks", 1, Vector(selected, foreign))),
        phaseValidationPolicy = Some(V.Policy("phase-checks", 1, Vector(phasecheck))))
      When("the same native policy selector constructs semantic instructions for the actual selected scope")
      val stepinstructions = take(V.sourceInstructionsC(declaration, selectedplan, Some("S1")))
      val phaseinstructions = take(V.sourceInstructionsC(declaration, selectedplan, None))
      val refused = V.sourceInstructionsC(declaration.copy(candidatePaths = Vector.empty), selectedplan, Some("S1"))
      val wrongcheck = selected.copy(metadata = Some(profile.copy(source = Some(source.copy(sliceRef = "S2")))))
      val wrongscope = V.sourceInstructionsC(declaration.copy(validationPolicy = Some(
        V.Policy("slice-checks", 1, Vector(wrongcheck)))), selectedplan, Some("S1"))
      val embedded = declaration.copy(validationPolicy = Some(V.Policy("embedded", 1, Vector(GoalPhaseNativeMetadataFixture.check))))
      Then("issued instructions preserve exact source details and mandatory Candidate review while invalid ownership or scope is refused")
      stepinstructions should include(source.path)
      stepinstructions should include(source.identity)
      source.acceptanceConditions.foreach(condition => stepinstructions should include(condition))
      stepinstructions should include("slice=S1")
      stepinstructions should include("independent review and admission")
      stepinstructions should not include "elsewhere.json"
      phaseinstructions should include("slice=phase:PHASE-6.2")
      refused.isSuccess shouldBe false
      wrongscope.isSuccess shouldBe false
      take(V.sourceInstructionsC(embedded, selectedplan, Some("S1"))) shouldBe ""
    }
  }
}
