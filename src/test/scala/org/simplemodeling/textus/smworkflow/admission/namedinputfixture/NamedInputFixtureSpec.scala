package org.simplemodeling.textus.smworkflow.admission.namedinputfixture

import io.circe.Json
import java.nio.file.Path
import org.goldenport.cncf.processexecution.SbtScenarioParameters.Value
import org.goldenport.cncf.validation.{ValidationDeclarationProvider, ValidationDiscoveryV1 as D, ValidationInputsV1 as I}
import org.goldenport.cncf.validation.ValidationMetadataV1.Declaration
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata as N,
  GoalPhaseNativeValidation as V, GoalPhaseSliceValidationPlan as L, WorkflowSbtValidation}

/** Trusted exact input names that collide under ordinary property naming.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
class NamedInputDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(requiredSemantics = Set("inputSchema"), inputSchema = Some(Vector(
    I.Parameter("case-label", I.Kind.Text, defaultValue = Some(Value.Text("single"))),
    I.Parameter("case--label", I.Kind.Text, defaultValue = Some(Value.Text("double"))),
    I.Parameter("tail-", I.Kind.Text, required = false, defaultValue = Some(Value.Text("tail")))
  )))
}

/** Independent actual registration; existing fixture inventories remain unchanged.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@ValidationSpecification(identity = "named-inputs", revision = "1", declaration = classOf[NamedInputDeclarations])
class NamedInputFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "named validation inputs" should {
    "retain distinct exact declarations" in {
      Given("the trusted declaration used by the actual named-inputs registration")
      val declaration = new NamedInputDeclarations().declaration
      When("the ordinary scenario observes its published input schema")
      val fields = declaration.inputSchema.get.map(p => (p.name, p.kind, p.required, p.defaultValue))
      Then("single repeated and trailing hyphens remain exact names with their text defaults")
      declaration.requiredSemantics shouldBe Set("inputSchema")
      fields shouldBe Vector(("case-label", I.Kind.Text, true, Some(Value.Text("single"))),
        ("case--label", I.Kind.Text, true, Some(Value.Text("double"))),
        ("tail-", I.Kind.Text, false, Some(Value.Text("tail"))))
    }
  }

  @ValidationScenario(name = "named validation inputs should retain distinct exact declarations", identity = "named-case",
    declaration = classOf[NamedInputDeclarations])
  def namedCaseAnchor(): Unit = ()
}

/** Source-bound selection through the real fixture artifact and producer APIs.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object NamedInputFixture {
  val fixtureClass = classOf[NamedInputFixtureSpec]
  val loader = fixtureClass.getClassLoader
  val reference = D.OperationReference("component:sm-workflow-named-inputs", "named-inputs", "named-case")
  def scope: D.DiscoveryScope = {
    val root = Path.of(fixtureClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    D.DiscoveryScope("sm-workflow-named-inputs", "scope-1", Vector(D.ArtifactScope(reference.component,
      "sm-workflow-named-input-tests", "artifact-1", "source-1", root,
      "org.simplemodeling.textus.smworkflow.admission.namedinputfixture")))
  }
  val source = L.Source("docs/slices/named-inputs.json", "named-inputs-plan", "S1", Vector("named-condition"))
  def profile: N.Profile = N.Profile(scope, D.SelectionQuery(), Vector.empty, Some(source))
  def check: V.Check = V.Check("named-inputs", 2, WorkflowSbtValidation.Kind.TestOnly, Some(profile))
  val globals: Map[String, Json] = Map("case-label" -> Json.fromString("global single"),
    "case--label" -> Json.fromString("global double"), "tail-" -> Json.fromString("global tail"))
  val variant: Map[String, Json] = Map("case-label" -> Json.fromString("variant single"),
    "case--label" -> Json.fromString("variant double"), "tail-" -> Json.fromString("variant tail"))
  def plan: L.Plan = L.Plan(source.identity, "revision-1", source.sliceRef,
    Vector(L.Condition("named-condition", "Exact named inputs survive the query and explicit second variant")),
    D.SelectionQuery(), Vector(L.Invocation(reference, variant)), globals,
    Vector(L.Coverage("named-condition", true, Vector(reference))))
}
