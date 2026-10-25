package org.simplemodeling.textus.smworkflow.admission.typedmetafixture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import io.circe.Json
import org.goldenport.cncf.processexecution.SbtScenarioParameters.Value
import org.goldenport.cncf.validation.{ValidationDeclarationProvider, ValidationDiscoveryV1 as D, ValidationInputsV1 as I}
import org.goldenport.cncf.validation.ValidationMetadataV1.Declaration
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}
import org.scalatest.{DoNotDiscover, GivenWhenThen, Outcome}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata as N,
  GoalPhaseNativeValidation as V, GoalPhaseSliceValidationPlan as L}

/** Trusted local schema for one ordinary ConfigMap-consuming registration.
  * @since   Oct. 19, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
class TypedMetadataDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(requiredSemantics = Set("inputSchema"), inputSchema = Some(Vector(
    I.Parameter("count", I.Kind.Integer, defaultValue = Some(Value.Integer(1L))),
    I.Parameter("enabled", I.Kind.BooleanValue, defaultValue = Some(Value.BooleanValue(true))),
    I.Parameter("label", I.Kind.Text, defaultValue = Some(Value.Text("default case")))
  )))
}

/** One actual annotated scenario, isolated from the existing three registrations.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
// Intentional helper: instantiated only by explicit metadata/input contract specifications.
@DoNotDiscover
@ValidationSpecification(identity = "typed-metadata-fixture", revision = "1", declaration = classOf[TypedMetadataDeclarations])
class TypedMetadataFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "typed metadata input" should {
    "retain its declared schema" in {
      Given("the local trusted declaration used by actual discovery and the ordinary fixture")
      val schema = new TypedMetadataDeclarations().declaration.inputSchema.get
      When("the ordinary ScalaTest scenario observes that declaration")
      val fields = schema.map(p => (p.name, p.kind, p.required, p.defaultValue))
      Then("all three exact kinds and typed defaults remain declared")
      fields shouldBe Vector(("count", I.Kind.Integer, true, Some(Value.Integer(1L))),
        ("enabled", I.Kind.BooleanValue, true, Some(Value.BooleanValue(true))),
        ("label", I.Kind.Text, true, Some(Value.Text("default case"))))
    }
  }

  @ValidationScenario(name = "typed metadata input should retain its declared schema", identity = "typed-case",
    declaration = classOf[TypedMetadataDeclarations])
  def typedCaseAnchor(): Unit = ()

  override protected def withFixture(test: NoArgTest): Outcome = {
    Given("the standard ScalaTest test.configMap and the same trusted typed schema")
    val schema = new TypedMetadataDeclarations().declaration.inputSchema.get
    When("the CNCF bridge consumes namespaced values and declared defaults before the body")
    val inputs = I.fromConfigMapC(schema, test.configMap.toMap)
    Then("the fixture receives all three admitted typed inputs")
    inputs.isSuccess shouldBe true
    inputs.toOption.get.keySet shouldBe Set("count", "enabled", "label")
    val bound = inputs.toOption.get
    Given("the exact optional test-host observation environment and the actual bound primitive map")
    val observation = sys.env.get("SMWORKFLOW_TYPED_INPUT_OBSERVATIONS")
    When("the test-only observer validates its fixed sandbox and appends one invocation if enabled")
    observation.foreach { value =>
      val path = Path.of(value)
      val code = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
      val target = Iterator.iterate(code)(_.getParent).takeWhile(_ != null)
        .find(path => Option(path.getFileName).exists(_.toString == "target"))
        .getOrElse(throw new IllegalArgumentException("Actual fixture code must be under the primary target"))
      val native = target.resolve("sm-workflow/work/native-cli")
      require(path.isAbsolute && path.normalize == path &&
        Option(path.getFileName).exists(_.toString == "typed-input-observations.jsonl") &&
        path.getParent != null && path.getParent.getParent == native &&
        Files.isDirectory(path.getParent) && path.getParent.toRealPath() == path.getParent &&
        (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
          (Files.isRegularFile(path) && path.toRealPath() == path)),
        "Typed input observer requires its exact canonical native-cli child sandbox")
      val primitive = Json.obj(bound.toVector.sortBy(_._1).map { (name, value) =>
        name -> (value match {
          case Value.Integer(value) => Json.fromLong(value)
          case Value.BooleanValue(value) => Json.fromBoolean(value)
          case Value.Text(value) => Json.fromString(value)
        })
      }*)
      Files.writeString(path, primitive.noSpaces + "\n", UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      Then("the opt-in host receives the actual canonical primitive map at its one fixed file")
      Files.isRegularFile(path) shouldBe true
      bound("count") shouldBe a[Value.Integer]
      bound("enabled") shouldBe a[Value.BooleanValue]
      bound("label") shouldBe a[Value.Text]
    }
    if (observation.isEmpty) {
      Then("without a host observer the ordinary schema bridge completes with no observation write")
      observation shouldBe None
      bound.size shouldBe 3
    }
    super.withFixture(test)
  }
}

/** Shared actual discovery inputs; no invented positive inventory or execution.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object TypedMetadataFixture {
  val fixtureClass = classOf[TypedMetadataFixtureSpec]
  val loader = fixtureClass.getClassLoader
  val reference = D.OperationReference("component:sm-workflow-typed-fixture", "typed-metadata-fixture", "typed-case")
  def scope: D.DiscoveryScope = {
    val root = Path.of(fixtureClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    D.DiscoveryScope("sm-workflow-typed-fixture", "scope-1", Vector(D.ArtifactScope(reference.component,
      "sm-workflow-typed-fixture-tests", "artifact-1", "source-1", root,
      "org.simplemodeling.textus.smworkflow.admission.typedmetafixture")))
  }
  val source = L.Source("docs/slices/typed-validation.json", "typed-validation", "S1", Vector("typed-condition"))
  def profile: N.Profile = N.Profile(scope, D.SelectionQuery(), Vector.empty, Some(source))
  def check: V.Check = V.Check("typed-metadata", 2, org.simplemodeling.textus.smworkflow.admission.WorkflowSbtValidation.Kind.TestOnly,
    Some(profile))
  def plan: L.Plan = L.Plan(source.identity, "revision-1", source.sliceRef,
    Vector(L.Condition("typed-condition", "The same discovered scenario is selected with two distinct counts")),
    D.SelectionQuery(), Vector(L.Invocation(reference, Map("count" -> io.circe.Json.fromInt(2)))), Map.empty,
    Vector(L.Coverage("typed-condition", true, Vector(reference))))
}
