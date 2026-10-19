package org.simplemodeling.textus.smworkflow.admission

import cats.~>
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Path
import scala.concurrent.duration.*
import org.goldenport.{Consequence, ConsequenceException}
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M, ValidationInputsV1 as I}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.metafixture.SelectedMetadataFixtureSpec
import org.simplemodeling.textus.smworkflow.admission.typedmetafixture.{TypedMetadataFixture as T, TypedMetadataDeclarations}
import org.simplemodeling.textus.smworkflow.admission.namedinputfixture.{NamedInputFixture as K, NamedInputDeclarations}
import GoalPhaseNativeMetadata as N
import GoalPhaseNativeValidation as V
import GoalPhaseSliceValidationPlan as L
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Shared real producer discovery, never a synthetic positive inventory.
  * @since   Oct. 18, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object GoalPhaseNativeMetadataFixture {
  val fixtureClass = classOf[SelectedMetadataFixtureSpec]
  val loader = fixtureClass.getClassLoader
  def profile: N.Profile = {
    val root = Path.of(fixtureClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    val artifact = D.ArtifactScope("component:sm-workflow-fixture", "sm-workflow-fixture-tests", "artifact-1", "source-1",
      root, "org.simplemodeling.textus.smworkflow.admission.metafixture")
    N.Profile(D.DiscoveryScope("sm-workflow-metadata-fixture", "scope-1", Vector(artifact)),
      D.SelectionQuery(Set(M.Purpose.SMOKE)),
      Vector(D.OperationReference("component:sm-workflow-fixture", "metadata-fixture", "focused-b")))
  }
  def check: V.Check = V.Check("selected-metadata", 2, WorkflowSbtValidation.Kind.TestOnly, Some(profile))
  def prepared: (Vector[V.Check], Vector[N.Batch]) = take(N.prepareC(Vector(check), loader))
  def batch: N.Batch = prepared._2.head
}

/** Actual selection and passive transport; synthetic return values test refusal
  * only and do not establish native execution or independent review.
  * @since   Oct. 18, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseNativeMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import N.given
  import GoalPhaseNativeMetadataFixture.*
  private val _snapshot = GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40)
  private def _payload(checks: Vector[V.Check], batches: Vector[N.Batch]) =
    V.Payload("8", "S1", _snapshot, checks, Vector.empty, None, batches)
  private def _configuration(value: Json): ConfigurationValue = value.fold(ConfigurationValue.NullValue,
    ConfigurationValue.BooleanValue.apply, number => ConfigurationValue.NumberValue(number.toBigDecimal.get),
    ConfigurationValue.StringValue.apply, values => ConfigurationValue.ListValue(values.map(_configuration).toList),
    fields => ConfigurationValue.ObjectValue(fields.toMap.view.mapValues(_configuration).toMap))
  private def _configured(value: Json): Consequence[ThreeWorkflowHostParameters.Definition] = {
    var selected = Option.empty[ThreeWorkflowHostParameters.Definition]
    val id = SmWorkflowComponent.componentId
    val host = ResolvedConfiguration(HostConfiguration(Map(ThreeWorkflowHostParameters.definition.name -> _configuration(value))),
      ConfigurationTrace.empty)
    val creation = ComponentCreate(RuntimeBindingAdmissionFixture.admit(Subsystem(name = "spec-native-metadata", version = None,
      configuration = host, aliasResolver = AliasResolver.empty, runMode = RunMode.Command)), ComponentOrigin.Builtin,
      Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name), version = Some("0.1.0"),
        schemaVersion = Some(3), componentId = Some(id))))
    val factory = new SmWorkflowConfiguredComponentFactory(Vector(ThreeWorkflowHostParameters.definition), Vector.empty,
      (_, parameters, context) => for {
        _ <- if (context != null) Consequence.unit else Consequence.stateConflict("Missing actual initialization context")
        setting <- parameters.resolve(ThreeWorkflowHostParameters.definition)
        definition <- setting.value.map(Consequence.success).getOrElse(Consequence.stateConflict("Missing host definition"))
        _ <- definition.goal.stepValidation.validateC
        _ <- definition.goal.phaseValidation.validateC
      } yield {
        selected = Some(definition)
        Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
      })
    factory.primaryFactory.createPrimaryC(creation).flatMap(_ => selected.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Host definition was not initialized")))
  }

  "Structured native metadata configuration" should {
    "discover the complete actual fixture from a source-bound captured plan and retain its enriched Batch" in {
      Given("an explicit source-only profile and the typed plan captured by the owning boundary, using the real compiled fixture")
      val sourcecheck = GoalPhaseSliceValidationPlanFixture.check
      val sliceplan = GoalPhaseSliceValidationPlanFixture.plan
      val sourceprofile = sourcecheck.metadata.get
      val definition = ThreeWorkflowNormalHostFixture.definition(Path.of("/private/tmp/smwf-source-plan-host"))
      val configured = definition.copy(goal = definition.goal.copy(stepValidation = V.Policy("source-checks", 1, Vector(sourcecheck))))
      When("actual component configuration resolves the source and native metadata prepares the captured typed plan")
      val initialized = take(_configured(ThreeWorkflowHostParameters.encode(configured)))
      val (selected, batches) = take(N.prepareC(initialized.goal.stepValidation.checks, loader,
        Map(sourcecheck.identity -> sliceplan)))
      val actual = batches.head
      val restored = actual.asJson.as[N.Batch]
      val rendered = RuntimeContext.PropertyNameContext.default.transformRecord(N.record(actual.asJson))
      val publicjson = io.circe.parser.parse(take(rendered.toJsonStringC)).toOption.get
      val normalized = N.normalizeBatches(Json.arr(publicjson))
      Then("the original source Profile exact typed Plan complete inventory and selected union survive both retained transports")
      initialized.goal.stepValidation.checks shouldBe Vector(sourcecheck)
      sourceprofile.query shouldBe D.SelectionQuery()
      sourceprofile.supplemental shouldBe Vector.empty
      actual.profile shouldBe sourceprofile
      actual.plan shouldBe Some(sliceplan)
      actual.inventory.completeness shouldBe D.Completeness.COMPLETE
      actual.inventory.operations.size shouldBe 3
      actual.references shouldBe batch.references
      restored shouldBe Right(actual)
      normalized shouldBe Right(Vector(actual).asJson)
      publicjson.hcursor.downField("plan").downField("execution_parameters").focus shouldBe Some(Json.obj())
      publicjson.hcursor.downField("plan").downField("additional_operations").downArray
        .downField("parameters").focus shouldBe Some(Json.obj())
      N.validateRetainedC(Vector(sourcecheck), selected, Vector(restored.toOption.get)).isSuccess shouldBe true
      N.validateRuntimeC(selected, batches).isSuccess shouldBe true
      V.codec.decode(V.codec.encode(_payload(selected, batches))) shouldBe Right(_payload(selected, batches))
      actual.asJson.hcursor.downField("plan").get[String]("revision") shouldBe Right(sliceplan.revision)
    }

    "refuse mixed embedded selection and explicit null optional source or plan fields" in {
      Given("an ordinary embedded fixture Profile and an explicit source contract that must solely own selection")
      val source = GoalPhaseSliceValidationPlanFixture.source
      val sourceprofile = GoalPhaseSliceValidationPlanFixture.profile
      val invalid = Vector(profile.copy(source = Some(source)),
        sourceprofile.copy(query = profile.query), sourceprofile.copy(supplemental = profile.supplemental))
      val actual = batch
      When("the real Profile Batch and Check codecs decode mixed configuration or explicit-null optional fields")
      val mixed = invalid.map(_.asJson.as[N.Profile])
      val nullsource = profile.asJson.mapObject(_.add("source", Json.Null)).as[N.Profile]
      val nullplan = actual.asJson.mapObject(_.add("plan", Json.Null)).as[N.Batch]
      val unknownsource = source.asJson.mapObject(_.add("fallback", Json.True))
      val unknownprofile = sourceprofile.asJson.mapObject(_.add("source", unknownsource)).as[N.Profile]
      Then("source ownership cannot combine with embedded query or supplemental configuration and absence keeps its original encoding")
      mixed.foreach(_.isLeft shouldBe true)
      nullsource.isLeft shouldBe true
      nullplan.isLeft shouldBe true
      unknownprofile.isLeft shouldBe true
      profile.asJson.hcursor.downField("source").focus shouldBe None
      actual.asJson.hcursor.downField("plan").focus shouldBe None
      profile.asJson.as[N.Profile] shouldBe Right(profile)
      actual.asJson.as[N.Batch] shouldBe Right(actual)
      invalid.foreach(p => V.Policy("mixed", 1, Vector(check.copy(metadata = Some(p)))).validateC.isSuccess shouldBe false)
    }

    "reconstruct retained source selection without file access and refuse crossed plans sources or actual coverage" in {
      Given("a real discovered source selection retained with its original Profile Plan inventory and exact references")
      val sourcecheck = GoalPhaseSliceValidationPlanFixture.check
      val sliceplan = GoalPhaseSliceValidationPlanFixture.plan
      val (selected, batches) = take(N.prepareC(Vector(sourcecheck), loader, Map(sourcecheck.identity -> sliceplan)))
      val actual = batches.head
      val missing = sliceplan.additionalOperations.head.reference.copy(operation = "absent-exact-reference")
      val wrongcoverage = sliceplan.copy(acceptanceCoverageRefs = sliceplan.acceptanceCoverageRefs.updated(1,
        L.Coverage("focused-condition", false, Vector(missing))))
      val wrongsource = actual.profile.copy(source = actual.profile.source.map(_.copy(path = "different-plan.json")))
      val emptyquery = sliceplan.copy(metadataSelection = D.SelectionQuery(features = Set(
        M.FeatureReference("component:sm-workflow-fixture", "absent"))))
      val variants = Vector(actual.copy(plan = None), actual.copy(plan = Some(sliceplan.copy(identity = "different-plan"))),
        actual.copy(plan = Some(sliceplan.copy(sliceRef = "S2"))), actual.copy(plan = Some(wrongcoverage)),
        actual.copy(plan = Some(emptyquery)), actual.copy(profile = wrongsource))
      When("pure retained validation and exact Batch decoding run with no core file target loader or execution argument")
      val retained = N.validateRetainedC(Vector(sourcecheck), selected, batches)
      val runtime = N.validateRuntimeC(selected, batches)
      val refused = variants.map(value => N.validateRetainedC(Vector(sourcecheck), selected, Vector(value)))
      val embeddedplan = N.bindC(batch.copy(plan = Some(sliceplan)))
      Then("the exact stored source selection survives and no missing crossed unsupported or uncovered plan can replace it")
      retained.isSuccess shouldBe true
      runtime.isSuccess shouldBe true
      take(N.bindC(actual)).map(_.operation.reference) shouldBe actual.references
      refused.foreach(_.isSuccess shouldBe false)
      variants.filterNot(_.profile == wrongsource).foreach(_.asJson.as[N.Batch].isLeft shouldBe true)
      embeddedplan.isSuccess shouldBe false
      actual.profile.source shouldBe sourcecheck.metadata.get.source
      actual.plan shouldBe Some(sliceplan)
    }

    "roundtrip the owning Check and Profile through actual component initialization parameters" in {
      Given("the real fixture artifact scope and a fixed TestOnly metadata host binding in a structured host definition")
      val ordinary = ThreeWorkflowNormalHostFixture.definition(Path.of("/private/tmp/smwf-metadata-host"))
      val definition = ordinary.copy(goal = ordinary.goal.copy(stepValidation = V.Policy("selected-checks", 1, Vector(check))))
      val wire = ThreeWorkflowHostParameters.encode(definition)
      When("the owning codecs and normal configured factory resolve that nested host configuration with an ExecutionContext")
      val restored = check.asJson.as[V.Check]
      val actual = _configured(wire)
      Then("the exact typed scope query supplemental references and literal binding survive without command parsing")
      restored shouldBe Right(check)
      profile.asJson.as[N.Profile] shouldBe Right(profile)
      take(actual) shouldBe definition
      take(actual).goal.stepValidation.checks shouldBe Vector(check)
    }

    "reject explicit nulls runtime-selected configuration and metadata on ordinary Test" in {
      Given("a valid metadata check and caller configuration variants with forbidden selected fields or an ordinary Test kind")
      val runtime = prepared._1.head
      val invalidchecks = Vector(runtime, check.copy(hostIdentity = Some("host")),
        check.copy(operation = runtime.operation), check.copy(kind = WorkflowSbtValidation.Kind.Test),
        check.copy(metadata = null), check.copy(hostIdentity = null), check.copy(operation = null))
      val nullfields = Vector("metadata", "hostIdentity", "operation").map(key => check.asJson.mapObject(_.add(key, Json.Null)))
      When("the owning decoder consumes explicit-null fields and the literal host Policy validates each supplied check")
      val malformed = nullfields.map(_.as[V.Check])
      val rejected = invalidchecks.map(value => V.Policy("selected-checks", 1, Vector(value)).validateC)
      Then("absence remains distinct from null and no runtime identity or metadata Test configuration is admitted")
      malformed.foreach(_.isLeft shouldBe true)
      rejected.foreach(_.isSuccess shouldBe false)
      val ordinary = V.Check("ordinary", 1)
      ordinary.asJson.as[V.Check] shouldBe Right(ordinary)
      ordinary.asJson.hcursor.downField("kind").focus shouldBe None
      V.Policy("ordinary-policy", 1, Vector(ordinary)).validateC.isSuccess shouldBe true
    }
  }

  "Exact named-input public transport" should {
    "retain distinct producer names through canonical normalization public projection and typed rebinding" in {
      Given("the actual named-inputs artifact source-bound Plan with global values and an explicit second variant")
      val plan = K.plan
      val (checks, batches) = take(N.prepareC(Vector(K.check), K.loader, Map(K.check.identity -> plan)))
      val actual = batches.head
      val expectedentries = K.globals.toVector.sortBy(_._1).map((name, value) =>
        Json.obj("name" -> Json.fromString(name), "value" -> value))
      val expectedvariant = K.variant.toVector.sortBy(_._1).map((name, value) =>
        Json.obj("name" -> Json.fromString(name), "value" -> value))
      When("the real CNCF property context renders the public Batch and the boundary restores and rebinds it")
      val canonical = N.normalizeBatches(batches.asJson)
      val rendered = RuntimeContext.PropertyNameContext.default.transformRecord(N.record(actual.asJson))
      val publicjson = io.circe.parser.parse(take(rendered.toJsonStringC)).toOption.get
      val normalized = N.normalizeBatches(Json.arr(publicjson))
      val restored = normalized.toOption.get.as[Vector[N.Batch]].toOption.get
      val operations = checks.map(check => take(N.operationC(check, restored)))
      Then("all exact names and per-variant values survive without business-key normalization or inventory invention")
      actual.inventory.completeness shouldBe D.Completeness.COMPLETE
      actual.inventory.operations.map(_.reference) shouldBe Vector(K.reference)
      actual.inventory.operations.head.metadata.get.inputSchema shouldBe
        take(I.validateSchemaC(new NamedInputDeclarations().declaration.inputSchema.get))
      actual.references shouldBe Vector.fill(2)(K.reference)
      actual.plan shouldBe Some(plan)
      actual.asJson.as[N.Batch] shouldBe Right(actual)
      canonical shouldBe Right(batches.asJson)
      publicjson.hcursor.get[String]("check_identity") shouldBe Right(actual.checkIdentity)
      publicjson.hcursor.downField("plan").get[Vector[Json]]("execution_parameters") shouldBe Right(expectedentries)
      publicjson.hcursor.downField("plan").downField("additional_operations").downArray
        .get[Vector[Json]]("parameters") shouldBe Right(expectedvariant)
      normalized shouldBe Right(batches.asJson)
      restored shouldBe batches
      operations.map(_.parameters) shouldBe Vector(K.globals, K.variant).map(_.map { (name, value) =>
        name -> SbtScenarioParameters.Value.Text(value.asString.get)
      })
      operations.map(_.intent.target.scenario.get.parameters) shouldBe operations.map(_.parameters)
      N.validateRetainedC(Vector(K.check), checks, restored).isSuccess shouldBe true
    }

    "keep private Plan maps exact and limit public entry projection to the two Batch Plan paths" in {
      Given("a real named-input Batch and unrelated structured fields with the same parameter dictionary spelling")
      val actual = take(N.prepareC(Vector(K.check), K.loader, Map(K.check.identity -> K.plan)))._2.head
      val plan = actual.plan.get
      val envelope = Json.obj("schemaVersion" -> Json.fromString(L.schemaVersion), "payload" -> plan.asJson)
      val unrelated = Json.obj("parameters" -> Json.obj("case-label" -> Json.fromString("outside")),
        "proposal" -> Json.obj("plan" -> plan.asJson))
      When("private codecs read canonical maps and the Record converter projects an unrelated structure")
      val decoded = L.decodeC(envelope.noSpaces)
      val rendered = io.circe.parser.parse(take(N.record(unrelated).toJsonStringC)).toOption.get
      val publicplan = io.circe.parser.parse(take(N.record(actual.asJson).toJsonStringC)).toOption.get
        .hcursor.downField("plan").focus.get
      Then("canonical codecs retain maps reject entry arrays and unrelated dictionaries remain ordinary objects")
      take(decoded) shouldBe plan
      plan.asJson.as[L.Plan] shouldBe Right(plan)
      plan.asJson.hcursor.downField("executionParameters").focus shouldBe Some(Json.obj(K.globals.toSeq*))
      plan.asJson.hcursor.downField("additionalOperations").downArray.downField("parameters").focus shouldBe
        Some(Json.obj(K.variant.toSeq*))
      publicplan.as[L.Plan].isLeft shouldBe true
      L.decodeC(envelope.mapObject(_.add("payload", publicplan)).noSpaces).isSuccess shouldBe false
      actual.asJson.mapObject(_.add("plan", publicplan)).as[N.Batch].isLeft shouldBe true
      rendered shouldBe unrelated
    }

    "give the existing Review binding the same canonical meaning after actual public projection" in {
      Given("a real named-input Batch and its ordinary canonical independent-review target")
      val batches = take(N.prepareC(Vector(K.check), K.loader, Map(K.check.identity -> K.plan)))._2
      val purpose = NativeHostEvidence.Purpose.GoalReview
      val target = NativeHostReviewEvidenceFixture.goalInput.mapObject(_.add("validationMetadata", batches.asJson))
      val canonicalbinding = NativeHostReviewEvidenceFixture.binding(purpose, Some(target))
      When("actual CNCF rendering supplies the public metadata to the existing selected Review scope")
      val publicjson = io.circe.parser.parse(take(RuntimeContext.PropertyNameContext.default
        .transformRecord(N.record(batches.head.asJson)).toJsonStringC)).toOption.get
      val publictarget = target.mapObject(_.add("validationMetadata", Json.arr(publicjson)))
      val selected = NativeHostReviewScope.select(purpose,
        NativeHostReviewEvidenceFixture.binding(purpose, Some(publictarget))).toOption.get
      val canonical = NativeHostReviewScope.select(purpose, canonicalbinding).toOption.get
      Then("Review selects the identical full canonical Plan schema and distinct named variant values")
      selected shouldBe canonical
      selected.hcursor.downField("attestation").downField("target")
        .get[Vector[N.Batch]]("validationMetadata") shouldBe Right(batches)
    }

    "refuse malformed public parameter entries at both exact Plan dictionary locations" in {
      Given("actual named-input public metadata and malformed entry shapes for each parameter dictionary")
      val actual = take(N.prepareC(Vector(K.check), K.loader, Map(K.check.identity -> K.plan)))._2.head
      val publicjson = io.circe.parser.parse(take(RuntimeContext.PropertyNameContext.default
        .transformRecord(N.record(actual.asJson)).toJsonStringC)).toOption.get
      val entry = Json.obj("name" -> Json.fromString("case-label"), "value" -> Json.fromString("single"))
      val badentries = Vector(Json.arr(), Json.arr(entry, entry), Json.arr(Json.Null), Json.arr(Json.True),
        Json.arr(entry.mapObject(_.remove("name"))), Json.arr(entry.mapObject(_.remove("value"))),
        Json.arr(entry.mapObject(_.add("extra", Json.True))),
        Json.arr(entry.mapObject(_.add("name", Json.Null))),
        Json.arr(entry.mapObject(_.add("name", Json.fromInt(1)))),
        Json.arr(entry.mapObject(_.add("name", Json.fromString("")))),
        Json.arr(entry.mapObject(_.add("value", Json.Null))),
        Json.arr(entry.mapObject(_.add("value", Json.obj()))),
        Json.arr(entry.mapObject(_.add("value", Json.arr()))))
      val variants = badentries.flatMap { entries => Vector(
        publicjson.hcursor.downField("plan").downField("execution_parameters").withFocus(_ => entries).top.get,
        publicjson.hcursor.downField("plan").downField("additional_operations").downArray
          .downField("parameters").withFocus(_ => entries).top.get)
      }
      When("the public boundary reconstructs exact named maps before any producer binding")
      val refused = variants.map(value => N.normalizeBatches(Json.arr(value)))
      Then("duplicates missing extra null empty or nonprimitive entries fail without merging filtering or coercion")
      refused.foreach(_.isLeft shouldBe true)
      refused.foreach(_.left.toOption.get should include("validation parameter"))
    }

    "leave unknown wrong-kind and null canonical input rejection with the actual producer" in {
      Given("the actual named-input schema and canonical maps or well-shaped entries carrying invalid producer data")
      val actual = take(N.prepareC(Vector(K.check), K.loader, Map(K.check.identity -> K.plan)))._2.head
      val schema = actual.inventory.operations.head.metadata.get.inputSchema
      val invalid = Vector(K.globals.updated("unknown-name", Json.fromString("unknown")),
        K.globals.updated("case-label", Json.fromInt(1)), K.globals.updated("case--label", Json.Null))
      val variants = invalid.map(parameters => actual.copy(plan = actual.plan.map(_.copy(executionParameters = parameters))))
      val entries = invalid.take(2).map(parameters => Json.fromValues(parameters.toVector.sortBy(_._1).map { (name, value) =>
        Json.obj("name" -> Json.fromString(name), "value" -> value)
      }))
      When("the producer binds the supplied maps and public normalization reaches the same exact Batch binder")
      val producer = invalid.map(I.bindC(schema, _))
      val canonical = variants.map(value => N.normalizeBatches(Json.arr(value.asJson)))
      val publicrefused = entries.map(value => N.normalizeBatches(Json.arr(actual.asJson.hcursor.downField("plan")
        .downField("executionParameters").withFocus(_ => value).top.get)))
      Then("invalid names kinds and explicit null values fail instead of disappearing or using declared defaults")
      producer.foreach(_.isSuccess shouldBe false)
      variants.foreach(value => N.bindC(value).isSuccess shouldBe false)
      canonical.foreach(_.isLeft shouldBe true)
      publicrefused.foreach(_.isLeft shouldBe true)
    }
  }

  "Actual metadata selection and retained intent" should {
    "roundtrip canonical producer input schema and exact primitive defaults while keeping no-input wire unchanged" in {
      Given("actual discovery of the isolated typed fixture and its producer-normalized schema")
      val (_, batches) = take(N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> T.plan)))
      val actual = batches.head
      val metadata = actual.inventory.operations.head.metadata.get
      val nodefault = metadata.copy(inputSchema = metadata.inputSchema.map(_.copy(required = false, defaultValue = None)))
      val exact = metadata.copy(inputSchema = metadata.inputSchema.map(p =>
        if (p.name == "count") p.copy(defaultValue = Some(SbtScenarioParameters.Value.Integer(9007199254740993L))) else p))
      When("the owning Batch and Metadata codecs encode and restore schema defaults")
      val wire = metadata.asJson
      val restored = actual.asJson.as[N.Batch]
      val defaults = wire.hcursor.get[Vector[Json]]("inputSchema").toOption.get.map(value =>
        value.hcursor.get[String]("name").toOption.get -> value.hcursor.downField("defaultValue").focus.get).toMap
      Then("integer boolean and text defaults retain exact JSON kinds including large integers optional defaults remain absent and no-input schema keeps the old wire")
      restored shouldBe Right(actual)
      metadata.inputSchema shouldBe new TypedMetadataDeclarations().declaration.inputSchema.get
      defaults shouldBe Map("count" -> Json.fromInt(1), "enabled" -> Json.True, "label" -> Json.fromString("default case"))
      wire.as[M.Metadata] shouldBe Right(metadata)
      batch.inventory.operations.foreach(_.metadata.get.asJson.hcursor.downField("inputSchema").focus shouldBe None)
      batch.asJson.as[N.Batch] shouldBe Right(batch)
      nodefault.asJson.as[M.Metadata] shouldBe Right(nodefault)
      nodefault.asJson.hcursor.get[Vector[Json]]("inputSchema").toOption.get.foreach(
        _.hcursor.downField("defaultValue").focus shouldBe None)
      exact.asJson.as[M.Metadata] shouldBe Right(exact)
    }

    "refuse malformed unknown null noncanonical and mismatched schema defaults without discarding bad values" in {
      Given("the actual typed Metadata wire and explicit negative schema or parameter mutations")
      val actual = take(N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> T.plan)))._2.head
      val metadata = actual.inventory.operations.head.metadata.get
      val wire = metadata.asJson
      val schema = wire.hcursor.get[Vector[Json]]("inputSchema").toOption.get
      val count = schema.head
      val badparameters = Vector(count.mapObject(_.add("defaultValue", Json.fromString("1"))),
        count.mapObject(_.add("defaultValue", Json.True)), count.mapObject(_.add("defaultValue", Json.Null)),
        count.mapObject(_.add("defaultValue", Json.fromBigDecimal(BigDecimal("1.5")))),
        count.mapObject(_.add("defaultValue", Json.fromBigInt(BigInt(Long.MaxValue) + 1))),
        count.mapObject(_.add("kind", Json.fromString("integer"))), count.mapObject(_.add("kind", Json.Null)),
        count.mapObject(_.add("required", Json.Null)), count.mapObject(_.remove("required")),
        count.mapObject(_.add("unknown", Json.True)), count.mapObject(_.add("default", Json.fromInt(1))))
      val variants = badparameters.map(value => wire.mapObject(_.add("inputSchema", Json.fromValues(schema.updated(0, value))))) ++
        Vector(wire.mapObject(_.add("inputSchema", Json.Null)), wire.mapObject(_.add("inputSchema", Json.True)),
          wire.mapObject(_.add("inputSchema", Json.fromValues(schema.reverse))),
          wire.mapObject(_.add("inputSchema", Json.fromValues(schema :+ count))),
          wire.mapObject(_.add("inputSchema", Json.fromValues(Vector.fill(17)(count)))),
          wire.mapObject(_.add("inputSchema", Json.arr(Json.Null))))
      val mismatched = metadata.copy(inputSchema = metadata.inputSchema.updated(0,
        metadata.inputSchema.head.copy(defaultValue = Some(SbtScenarioParameters.Value.Text("wrong kind")))))
      When("exact metadata decoding and retained producer admission consume every malformed presence")
      val refused = variants.map(_.as[M.Metadata]) :+ mismatched.asJson.as[M.Metadata]
      val invalidinventory = actual.inventory.copy(operations = actual.inventory.operations.map(operation => operation.copy(metadata = Some(mismatched))))
      Then("unknown fields aliases null defaults bad kinds and noncanonical order fail instead of being normalized away")
      refused.foreach(_.isLeft shouldBe true)
      mismatched.asJson.hcursor.downField("inputSchema").downArray.get[String]("defaultValue") shouldBe Right("wrong kind")
      invalidinventory.asJson.as[D.Inventory].isLeft shouldBe true
      N.bindC(actual.copy(inventory = invalidinventory)).isSuccess shouldBe false
      wire.mapObject(_.remove("inputSchema")).as[M.Metadata].toOption.get.inputSchema shouldBe empty
    }

    "route each repeated reference to its own exact expanded check and typed parameter map" in {
      Given("a captured full Plan with a query default and two equal explicit second variants")
      val plan = T.plan.copy(additionalOperations = T.plan.additionalOperations ++ T.plan.additionalOperations)
      val singletonplan = T.plan.copy(additionalOperations = Vector.empty)
      When("actual discovery binds variants and operationC resolves each exact expanded check by position")
      val (checks, batches) = take(N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> plan)))
      val operations = checks.map(check => take(N.operationC(check, batches)))
      val restored = batches.head.asJson.as[N.Batch].toOption.get
      val singleton = take(N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> singletonplan)))
      Then("distinct variants keep matching typed values with ordinal suffixes on every repeated reference while singleton identity stays unchanged")
      batches.head.references shouldBe Vector.fill(2)(T.reference)
      batches.head.plan shouldBe Some(plan)
      checks.map(_.identity) shouldBe Vector(1, 2).map(index => N.logicalIdentity(T.check.identity, T.reference) + ":invocation:" + index)
      checks.map(_.operation) shouldBe Vector.fill(2)(Some(T.reference))
      operations.map(_.parameters("count")) shouldBe Vector(SbtScenarioParameters.Value.Integer(1L), SbtScenarioParameters.Value.Integer(2L))
      operations.map(_.intent.target.scenario.get.parameters) shouldBe operations.map(_.parameters)
      N.validateRetainedC(Vector(T.check), checks, Vector(restored)).isSuccess shouldBe true
      N.operationC(checks.head.copy(identity = N.logicalIdentity(T.check.identity, T.reference)), batches).isSuccess shouldBe false
      N.operationC(checks.head.copy(operation = Some(T.reference.copy(operation = "foreign"))), batches).isSuccess shouldBe false
      N.validateRetainedC(Vector(T.check), checks.reverse, batches).isSuccess shouldBe false
      N.bindC(batches.head.copy(references = Vector(T.reference))).isSuccess shouldBe false
      singleton._1.map(_.identity) shouldBe Vector(N.logicalIdentity(T.check.identity, T.reference))
    }

    "reselect typed variants purely from retained Plan and inventory under an unavailable artifact-root context" in {
      Given("actual discovered typed rows and their full Plan retained with an explicitly unavailable root context")
      val (checks, batches) = take(N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> T.plan)))
      val actual = batches.head
      val scope = actual.profile.scope.copy(artifacts = actual.profile.scope.artifacts.map(_.copy(
        root = Path.of("/sm-workflow-unavailable-typed-artifact/classes"))))
      val profile = actual.profile.copy(scope = scope)
      val retained = actual.copy(profile = profile, inventory = actual.inventory.copy(scope = scope))
      val requested = T.check.copy(metadata = Some(profile))
      When("pure codecs and retained selection run with no core file loader rediscovery or replay argument")
      val restored = retained.asJson.as[N.Batch]
      val selected = N.validateRetainedC(Vector(requested), checks, Vector(retained))
      val bound = take(N.bindC(retained))
      Then("the same full Plan schema and ordered typed variants survive without claiming fresh artifact availability")
      restored shouldBe Right(retained)
      selected.isSuccess shouldBe true
      retained.plan shouldBe actual.plan
      retained.inventory.operations shouldBe actual.inventory.operations
      bound.map(_.parameters("count")) shouldBe Vector(SbtScenarioParameters.Value.Integer(1L), SbtScenarioParameters.Value.Integer(2L))
      checks.map(check => take(N.operationC(check, Vector(retained))).parameters) shouldBe bound.map(_.parameters)
      V.codec.decode(V.codec.encode(_payload(checks, Vector(retained)))) shouldBe Right(_payload(checks, Vector(retained)))
    }

    "include full captured Plan schema and variant values in the existing Review binding" in {
      Given("a real typed Batch and the ordinary selected independent-review target")
      val (checks, batches) = take(N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> T.plan)))
      val context = GoalPhaseNativeReview.Context(Vector(T.source.path), "actual typed fixture context", Vector.empty, batches)
      val target = NativeHostReviewEvidenceFixture.goalInput.mapObject(_.add("validationMetadata", batches.asJson))
      val purpose = NativeHostEvidence.Purpose.GoalReview
      val binding = NativeHostReviewEvidenceFixture.binding(purpose, Some(target))
      val changedplan = T.plan.copy(additionalOperations = Vector(L.Invocation(T.reference, Map("count" -> Json.fromInt(9)))))
      val changed = take(N.prepareC(Vector(T.check), T.loader, Map(T.check.identity -> changedplan)))._2
      val request = NativeHostHumanEvidenceFixture.work.copy(checkFailure = None, review = Some(context))
      When("the owning review codec and existing selected scope retain that full Batch and a changed valid variant")
      val restored = context.asJson.as[GoalPhaseNativeReview.Context]
      val selected = NativeHostReviewScope.select(purpose, binding).toOption.get
      val crossed = NativeHostReviewScope.select(purpose, NativeHostReviewEvidenceFixture.binding(purpose,
        Some(target.mapObject(_.add("validationMetadata", changed.asJson))))).toOption.get
      val restoredrequest = GoalPhaseNativeWork.inputCodec.decode(GoalPhaseNativeWork.inputCodec.encode(request))
      Then("the existing binding includes the exact Plan and schema and changes when supplied parameter meaning changes")
      restored shouldBe Right(context)
      selected.hcursor.downField("attestation").downField("target").get[Vector[N.Batch]]("validationMetadata") shouldBe Right(batches)
      selected should not be crossed
      restoredrequest shouldBe Right(request)
      N.validateRuntimeC(checks, batches).isSuccess shouldBe true
      batches.head.plan.get.additionalOperations.head.parameters("count") shouldBe Json.fromInt(2)
      batches.head.inventory.operations.head.metadata.get.inputSchema shouldBe new TypedMetadataDeclarations().declaration.inputSchema.get
    }

    "retain complete declared execution requirement options without treating them as runtime grants" in {
      Given("the real focused declaration requirement and additional typed disposition and reasoning-class declaration values")
      val original = batch.inventory.operations.find(_.reference.operation == "focused-b").get.metadata.get.executionRequirement.get
      val requirement = original.copy(disposition = Some(ExecutionDisposition(ExecutionPlacement.Delegated,
        ExecutionIndependence.Required, "selected-origin")), reasoningClass = Some(ReasoningClassReference("semantic-review")))
      When("the consumer's owning exact ExecutionRequirement codec roundtrips those typed declaration fields")
      val wire = requirement.asJson
      val restored = wire.as[ExecutionRequirement]
      Then("capabilities risk reasoning review disposition and reasoning class remain exact ordinary context")
      restored shouldBe Right(requirement)
      wire.hcursor.downField("disposition").get[String]("placement") shouldBe Right("DELEGATED")
      wire.hcursor.downField("disposition").get[String]("independence") shouldBe Right("REQUIRED")
      wire.hcursor.downField("reasoningClass").get[String]("value") shouldBe Right("semantic-review")
      original.disposition shouldBe None
      original.reasoningClass shouldBe None
    }

    "expand a real query and out-of-query supplement in stable order under one workspace ordinal" in {
      Given("the real compiled fixture with smoke-a queried and focused-b explicitly supplemental")
      val requested = Vector(check)
      When("the native boundary owns a bounded source loader and delegates discovery selection and exact binding")
      val (checks, batches) = take(N.prepareC(requested, loader))
      val batch = batches.head
      val operations = take(N.bindC(batch))
      Then("two exact operations retain full inventory stable distinct logical identities and the original controller binding")
      batch.inventory.operations.size shouldBe 3
      batch.inventory.completeness shouldBe D.Completeness.COMPLETE
      batch.references.map(_.operation) shouldBe Vector("smoke-a", "focused-b")
      checks.map(_.ordinal) shouldBe Vector(2, 2)
      checks.map(_.hostIdentity) shouldBe Vector.fill(2)(Some(check.identity))
      checks.map(_.identity).distinct.size shouldBe 2
      checks.map(_.identity) shouldBe batch.references.map(N.logicalIdentity(check.identity, _))
      checks.forall(c => c.metadata.isEmpty && c.kind == WorkflowSbtValidation.Kind.TestOnly) shouldBe true
      N.declaredIdentities(checks) shouldBe Vector(check.identity)
      operations.zip(checks).foreach { (bound, selected) =>
        selected.operation shouldBe Some(bound.operation.reference)
        bound.intent.operation shouldBe SbtOperation.TestOnly
        bound.intent.target shouldBe SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-2")),
          Some(SbtScenarioSelection(bound.operation.selector.suiteClassName, bound.operation.selector.testName)))
        take(N.operationC(selected, batches)).operation shouldBe bound.operation
      }
    }

    "roundtrip the actual complete Batch including provenance duration origins and execution requirement" in {
      Given("a real discovered batch whose focused declaration carries 500ms and a distinctive metadata-fixture capability")
      val actual = batch
      When("the owning Batch codec persists and decodes the full actual producer inventory")
      val wire = actual.asJson
      val restored = wire.as[N.Batch].toOption.get
      val focused = restored.inventory.operations.find(_.reference.operation == "focused-b").get
      Then("all inventory scope selectors revisions and normalized metadata survive exactly without Double duration conversion")
      restored shouldBe actual
      restored.inventory.scope shouldBe profile.scope
      restored.inventory.operations.size shouldBe 3
      restored.inventory.operations.map(_.provenance) shouldBe actual.inventory.operations.map(_.provenance)
      focused.metadata.get.expectedDuration shouldBe Some(500.millis)
      focused.metadata.get.expectedDuration.get.unit shouldBe java.util.concurrent.TimeUnit.NANOSECONDS
      focused.metadata.get.executionRequirement shouldBe Some(ExecutionRequirement(Vector(CapabilityRequirement("metadata-fixture")),
        RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false))
      focused.metadata.get.origins shouldBe M.Origins(true, true)
      focused.metadata.get.asJson.hcursor.get[Long]("expectedDurationNanos") shouldBe Right(500000000L)
      restored.inventory.diagnostics shouldBe actual.inventory.diagnostics
      restored.asJson shouldBe wire
    }

    "normalize actual CNCF public Record names to the exact canonical Batch" in {
      Given("a real full producer Batch rendered by CNCF's default public property naming context")
      val actual = batch
      val rendered = RuntimeContext.PropertyNameContext.default.transformRecord(N.record(actual.asJson))
      val publicjson = io.circe.parser.parse(take(rendered.toJsonStringC)).fold(
        error => fail("CNCF public Batch JSON could not be read: " + error.message), identity)
      publicjson.hcursor.get[String]("check_identity") shouldBe Right(actual.checkIdentity)
      publicjson.hcursor.downField("checkIdentity").focus shouldBe None
      When("the public metadata collection is normalized and decoded through the exact Batch boundary")
      val canonical = N.normalizeBatches(Json.arr(publicjson)).fold(
        error => fail("CNCF public Batch normalization failed: " + error), identity)
      val restored = canonical.as[Vector[N.Batch]].fold(
        error => fail("Canonical Batch decoding failed: " + error.message), identity)
      Then("the complete inventory empty arrays duration and execution requirements retain their original values")
      restored shouldBe Vector(actual)
      canonical shouldBe Vector(actual).asJson
      restored.head.inventory.operations.size shouldBe 3
      canonical.hcursor.downArray.downField("inventory").get[Vector[Json]]("diagnostics") shouldBe Right(Vector.empty)
      canonical.hcursor.downArray.downField("profile").downField("query").get[Vector[Json]]("features") shouldBe
        Right(Vector.empty)
      val focused = restored.head.inventory.operations.find(_.reference.operation == "focused-b")
        .getOrElse(fail("Real inventory must retain focused-b"))
      focused.metadata.flatMap(_.expectedDuration) shouldBe Some(500.millis)
      focused.metadata.flatMap(_.executionRequirement) shouldBe Some(ExecutionRequirement(
        Vector(CapabilityRequirement("metadata-fixture")), RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false))
      N.normalizeBatches(Json.arr()) shouldBe Right(Json.arr())
    }

    "refuse ambiguous nested public names and unknown or malformed nested metadata" in {
      Given("actual CNCF-rendered metadata with an equal-valued duplicate spelling or one invalid nested declaration")
      val actual = batch
      val rendered = RuntimeContext.PropertyNameContext.default.transformRecord(N.record(actual.asJson))
      val publicjson = io.circe.parser.parse(take(rendered.toJsonStringC)).fold(
        error => fail("CNCF public Batch JSON could not be read: " + error.message), identity)
      val index = actual.inventory.operations.indexWhere(_.reference.operation == "focused-b")
      index should be >= 0
      def _metadata_variant_(change: Json => Json): Json = publicjson.hcursor.downField("inventory")
        .downField("operations").downN(index).downField("metadata").withFocus(change).top
        .getOrElse(fail("Real public Batch must contain the focused metadata declaration"))
      val ambiguous = _metadata_variant_(_.mapObject(_.add("durationClass", Json.fromString("NORMAL"))))
      val invalid = Vector(
        _metadata_variant_(_.mapObject(_.add("unknown_nested_key", Json.True))),
        _metadata_variant_(_.mapObject(_.add("duration_class", Json.fromInt(1)))),
        _metadata_variant_(_.mapObject(_.add("duration_class", Json.fromString("FUTURE")))),
        _metadata_variant_(_.mapObject(_.add("execution_requirement", Json.Null))))
      When("the public boundary normalizes each collection before exact domain decoding")
      val collision = N.normalizeBatches(Json.arr(ambiguous))
      val refused = invalid.map(value => N.normalizeBatches(Json.arr(value)))
      Then("even agreeing aliases are ambiguous and unknown fields types enums or explicit nulls remain invalid")
      collision.left.toOption.getOrElse(fail("Nested duplicate names must fail")) should include("Ambiguous")
      refused.foreach(_.isLeft shouldBe true)
    }

    "reject malformed enum duration path reference oversized and explicit-null codec values" in {
      Given("real discovered producer values and distinct malformed wire variants at each consumed typed boundary")
      val actual = batch
      val metadata = actual.inventory.operations.find(_.reference.operation == "focused-b").get.metadata.get.asJson
      val badmetadata = Vector(metadata.mapObject(_.add("durationClass", Json.fromString("FUTURE"))),
        metadata.mapObject(_.add("purposes", Json.arr(Json.fromString("UNKNOWN")))),
        metadata.mapObject(_.add("expectedDurationNanos", Json.fromBigDecimal(BigDecimal("1.5")))),
        metadata.mapObject(_.add("expectedDurationNanos", Json.fromBigInt(BigInt(Long.MaxValue) + 1))),
        metadata.mapObject(_.add("expectedDurationNanos", Json.fromLong(0))),
        metadata.mapObject(_.add("expectedDurationNanos", Json.Null)),
        metadata.mapObject(_.add("executionRequirement", Json.Null)),
        metadata.mapObject(_.add("origins", Json.Null)))
      val badprofiles = Vector(profile.asJson.mapObject(_.add("supplemental", Json.Null)),
        profile.copy(supplemental = Vector.fill(17)(actual.references.head)).asJson,
        profile.copy(scope = profile.scope.copy(artifacts = Vector.empty)).asJson,
        profile.copy(scope = profile.scope.copy(artifacts = Vector.fill(17)(profile.scope.artifacts.head))).asJson)
      When("the exact consumer codecs decode malformed metadata profiles paths references and enum values")
      val metadataresults = badmetadata.map(_.as[M.Metadata])
      val profileresults = badprofiles.map(_.as[N.Profile])
      val pathresults = Vector("relative/classes", "/private/tmp/../classes").map(Json.fromString(_).as[Path])
      val referenceresults = Vector(actual.references.head.asJson.mapObject(_.add("operation", Json.Null)),
        actual.references.head.asJson.mapObject(_.add("component", Json.fromInt(1)))).map(_.as[D.OperationReference])
      Then("every malformed presence fails rather than losing a field changing a value or fabricating producer success")
      metadataresults.foreach(_.isLeft shouldBe true)
      profileresults.foreach(_.isLeft shouldBe true)
      pathresults.foreach(_.isLeft shouldBe true)
      referenceresults.foreach(_.isLeft shouldBe true)
      actual.asJson.mapObject(_.add("inventory", Json.Null)).as[N.Batch].isLeft shouldBe true
      Json.fromString("FUTURE").as[D.Completeness].isLeft shouldBe true
      Json.fromString("SUPER").as[ReasoningLevel].isLeft shouldBe true
      Json.fromString("REMOTE").as[ExecutionPlacement].isLeft shouldBe true
      Json.fromString("ASSUMED").as[ExecutionIndependence].isLeft shouldBe true
    }

    "reselect and rebind retained actual input without a loader and reject crossed or missing selections" in {
      Given("a fully persisted actual selection and no loader argument on the retained validation boundary")
      val (checks, batches) = prepared
      val actual = batches.head
      When("the retained Batch is decoded reselected and rebound while invalid host profiles references and identities are supplied separately")
      val restored = actual.asJson.as[N.Batch].toOption.get
      val retained = N.validateRetainedC(Vector(check), checks, Vector(restored))
      val refused = Vector(N.validateRetainedC(Vector(check), checks, Vector.empty),
        N.validateRetainedC(Vector(check.copy(metadata = Some(profile.copy(query = D.SelectionQuery())))), checks, batches),
        N.validateRetainedC(Vector(check), checks, Vector(actual.copy(references = actual.references.reverse))),
        N.validateRetainedC(Vector(check), checks.updated(0, checks.head.copy(identity = "incorrect")), batches),
        N.validateRetainedC(Vector(check), checks, Vector(actual.copy(checkIdentity = "another-host"))))
      Then("only the exact original selection survives and all bound selectors still identify the discovered registrations")
      retained.isSuccess shouldBe true
      take(N.bindC(restored)).map(_.operation.reference) shouldBe actual.references
      refused.foreach(_.isSuccess shouldBe false)
      val payload = _payload(checks, batches)
      V.codec.decode(V.codec.encode(payload)) shouldBe Right(payload)
      val request = NativeHostHumanEvidenceFixture.work.copy(checkFailure = None,
        review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "actual diff", Vector.empty, batches)))
      GoalPhaseNativeWork.inputCodec.decode(GoalPhaseNativeWork.inputCodec.encode(request)) shouldBe Right(request)
    }

    "omit null object attributes while preserving empty collections exact integers and non-null sequence contents" in {
      Given("the actual selected Batch and root or nested null attributes beside empty collections and exact non-null values")
      val actual = batch
      val value = Json.obj("metadata" -> actual.asJson, "empty" -> Json.arr(), "absentValue" -> Json.Null,
        "emptyObject" -> Json.obj(), "exactInteger" -> Json.fromLong(9007199254740993L),
        "nested" -> Json.obj("absentNested" -> Json.Null,
          "values" -> Json.arr(Json.fromString("first"), Json.arr(), Json.obj(), Json.fromLong(9007199254740993L), Json.False)))
      val expected = Json.obj("metadata" -> actual.asJson, "empty" -> Json.arr(), "emptyObject" -> Json.obj(),
        "exactInteger" -> Json.fromLong(9007199254740993L), "nested" -> Json.obj(
          "values" -> Json.arr(Json.fromString("first"), Json.arr(), Json.obj(), Json.fromLong(9007199254740993L), Json.False)))
      When("the bounded pure converter projects objects recursively into full Records and JSON reads the resulting Record")
      val projected = N.record(value)
      val restored = io.circe.parser.parse(take(projected.toJsonStringC)).toOption.get
      Then("Core omits null object attributes while exact non-null values array positions and the actual Batch survive")
      restored shouldBe expected
      restored.hcursor.get[Long]("exactInteger") shouldBe Right(9007199254740993L)
      restored.hcursor.downField("absentValue").focus shouldBe None
      restored.hcursor.downField("nested").downField("absentNested").focus shouldBe None
      restored.hcursor.get[Vector[Json]]("empty") shouldBe Right(Vector.empty)
      restored.hcursor.get[N.Batch]("metadata") shouldBe Right(actual)
    }

    "refuse direct and nested null sequence positions through the actual Core exception" in {
      Given("structured JSON arrays with a null direct member or a null member inside a nested array")
      val values = Vector(Json.obj("values" -> Json.arr(Json.fromString("first"), Json.Null)),
        Json.obj("nested" -> Json.obj("values" -> Json.arr(Json.arr(Json.True, Json.Null)))))
      When("the actual Record converter asks Core to own each sequence without filtering positions")
      val failures = values.map(value => intercept[ConsequenceException] { N.record(value) })
      Then("both projections fail with Core's specific null-sequence reason")
      failures.foreach(_.getMessage should include("Null Record sequence element"))
    }

    "refuse COMPLETE empty and explicitly negative PARTIAL or UNKNOWN retained eligibility" in {
      Given("real discovered rows and explicitly synthetic negative inventory completeness or unmatched-query variants")
      val actual = batch
      val emptyprofile = profile.copy(query = D.SelectionQuery(features = Set(M.FeatureReference("component:sm-workflow-fixture", "absent"))),
        supplemental = Vector.empty)
      val variants = Vector(actual.copy(profile = emptyprofile, references = Vector.empty),
        actual.copy(inventory = actual.inventory.copy(completeness = D.Completeness.PARTIAL)),
        actual.copy(inventory = actual.inventory.copy(completeness = D.Completeness.UNKNOWN)))
      When("each variant asks for exact retained binding and required-batch eligibility")
      val refusals = variants.map(N.bindC)
      Then("empty or incomplete inventory never becomes a passing required metadata batch")
      refusals.foreach(_.isSuccess shouldBe false)
      variants.foreach(_.asJson.as[N.Batch].isLeft shouldBe true)
      actual.inventory.completeness shouldBe D.Completeness.COMPLETE
      actual.references should not be empty
    }

    "retain explicit negative inventory diagnostics while refusing required-batch eligibility" in {
      Given("a real discovered inventory and an explicitly synthetic PARTIAL negative input with a producer-known enumeration diagnostic")
      val actual = batch
      val diagnostic = D.Diagnostic("enumeration", "negative-fixture", "Synthetic partial enumeration input")
      val partial = actual.inventory.copy(completeness = D.Completeness.PARTIAL, diagnostics = Vector(diagnostic))
      When("the exact inventory codec retains that negative context and Batch eligibility reselects it")
      val restored = partial.asJson.as[D.Inventory]
      val refused = N.bindC(actual.copy(inventory = partial))
      Then("the diagnostic and full real rows survive without promoting partial discovery to a runnable selection")
      restored shouldBe Right(partial)
      restored.toOption.get.diagnostics shouldBe Vector(diagnostic)
      restored.toOption.get.operations shouldBe actual.inventory.operations
      refused.isSuccess shouldBe false
      refused.toString should include("PARTIAL")
      refused.toString should include(diagnostic.message)
    }

    "bound mixed groups to sixteen expanded checks with literal host mapping and no ordinal collisions" in {
      Given("one real two-operation metadata host binding and fourteen distinct ordinary fixed host bindings")
      val ordinary = (1 to 15).filterNot(_ == 2).toVector.map(index => V.Check("ordinary-" + index, index))
      val requested = ordinary.take(1) ++ Vector(check) ++ ordinary.drop(1)
      When("the mixed selection ordinary-only selection and crossed or oversized selections are prepared")
      val (mixed, batches) = take(N.prepareC(requested, loader))
      val plain = take(N.prepareC(ordinary, loader))
      val toomany = N.prepareC(requested :+ V.Check("seventeenth-expanded", 16), loader)
      val collided = N.prepareC(Vector(check, V.Check("other-host", 2)), loader)
      val otherprofile = N.prepareC(Vector(check, check.copy(identity = "other-profile")), loader)
      Then("only sixteen distinct exact checks are admitted and original declared controller ids stay unchanged")
      mixed.size shouldBe 16
      N.declaredIdentities(mixed) shouldBe requested.map(_.identity)
      N.validateRuntimeC(mixed, batches).isSuccess shouldBe true
      plain shouldBe ((ordinary, Vector.empty))
      toomany.isSuccess shouldBe false
      collided.isSuccess shouldBe false
      otherprofile.isSuccess shouldBe false
      N.logicalIdentity("a", D.OperationReference("bc", "d", "e")) should not be
        N.logicalIdentity("ab", D.OperationReference("c", "d", "e"))
    }

    "keep the controller's literal selected host id and stable per-operation TEST issue identity across changed candidates" in {
      Given("real expanded metadata checks and explicitly synthetic terminated negative facts used only for controller and issue accounting")
      val (checks, batches) = prepared
      val plan = O.Plan("metadata-plan", "1", "PHASE-6.2", "selected-repository",
        Vector(O.Step("S1", "Selected metadata behavior", Vector("one.scala"), Vector.empty, Vector(check.identity))),
        Vector(check.identity))
      val initial = take(O.startC(plan, _snapshot))
      val key = initial.next.asInstanceOf[O.Semantic].key
      val inspected = take(take(initial.acceptC(O.SemanticCompleted(key, "Specification source ready")))
        .acceptC(O.CandidateObserved(_snapshot)))
      val receipt = RepositorySyncNativeMergeValidation.Receipt(checks.head.identity, "exited", Some(7),
        "synthetic negative stdout", "synthetic negative stderr", Some(WorkflowSbtValidation.Facts(
          WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Outcome.Failed, None, None)))
      val payload = _payload(checks, batches).copy(receipts = Vector(receipt), outcome = Some("failed"))
      When("native observation maps expanded checks back to the literal plan and TEST accounting sees a changed candidate later")
      val observed = take(V.observedStateC(inspected, payload, "selected-validation"))
      val first = take(GoalPhaseFixIssues.validationC("selected-instance", plan.repository, None, payload, "first-negative"))
      val changed = payload.copy(candidate = _snapshot.copy(tree = "c" * 40))
      val later = take(GoalPhaseFixIssues.validationC("selected-instance", plan.repository, first, changed, "later-negative"))
      Then("the strict controller comparison succeeds without adding operation names to its plan or duplicating the same TEST issue")
      observed.stage shouldBe O.Stage.Decision
      observed.plan.steps.head.checks shouldBe Vector(check.identity)
      observed.repairs shouldBe 0
      observed.ordinaryRepairs shouldBe 0
      later.get.issues.map(_.identity) shouldBe first.get.issues.map(_.identity)
      later.get.issues.size shouldBe 1
      later.get.issues.head.source shouldBe "TEST"
      later.get.issues.head.sourceIdentity shouldBe Some("test-only:" + checks.head.identity)
    }

    "reject explicitly synthetic wrong target scenario and operation returns for real selected metadata checks" in {
      Given("a real selected bound operation and explicitly synthetic negative typed returns with no native driver or authority")
      val bound = take(N.bindC(batch)).head
      val capture = ProcessExecutionCapture(Vector.empty, 0L, false)
      val raw = ProcessExecutionResult(ProcessExecutionTermination.TimedOut, capture, capture, Vector.empty, 0L, "synthetic-negative")
      val wrongworkspace = SbtTarget(take(WorkspaceId.parseC("foreign-workspace")), bound.intent.target.scenario)
      val wrongscenario = bound.intent.target.copy(scenario = Some(SbtScenarioSelection(fixtureClass.getName, "foreign scenario")))
      val negative = Vector(SbtExecutionResult(wrongworkspace, SbtOperation.TestOnly, raw),
        SbtExecutionResult(wrongscenario, SbtOperation.TestOnly, raw),
        SbtExecutionResult(bound.intent.target, SbtOperation.Test, raw))
      When("the actual thin validation method receives each deliberate synthetic return through its owning test UoW interpreter")
      val observed = Vector.newBuilder[UnitOfWorkOp[?]]
      val refusals = negative.map { supplied =>
        val base = ExecutionContext.create()
        val interpreter = new (UnitOfWorkOp ~> Consequence) {
          def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
            case UnitOfWorkOp.SbtExec(target, operation) =>
              observed += UnitOfWorkOp.SbtExec(target, operation)
              Consequence.success(supplied)
            case _ => Consequence.stateConflict("Unexpected synthetic negative operation")
          }
        }
        lazy val runtime: RuntimeContext = new RuntimeContext(
          RuntimeContext.core("synthetic-negative", None, base.observability), () => new UnitOfWork(context), interpreter,
          _ => (), _ => (), _ => (), "synthetic-negative")
        lazy val context: ExecutionContext = ExecutionContext.withRuntimeContext(base, runtime)
        WorkflowManagedProcessExecution.validationOperationC(ActionCall.Core(null, context, None, None), bound)
      }
      Then("all three mismatches fail before receipt creation and only the exact selected typed TestOnly intent was requested")
      refusals.foreach(_.isSuccess shouldBe false)
      observed.result() shouldBe Vector.fill(3)(bound.intent)
      WorkflowManagedProcessExecution.validationOperationC(null, bound).isSuccess shouldBe false
      negative.foreach(_.processResult.termination shouldBe ProcessExecutionTermination.TimedOut)
    }
  }
}
